package net.lecousin.dataformat.archive.cab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import net.lecousin.compression.mszip.MSZipReadable;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.io.buffering.SimpleBufferedReadable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;

public class CabFile {

	public static CabFile openReadOnly(IO.Readable io, WorkProgress progress, long work) {
		CabFile cab = new CabFile(io);
		cab.load(progress, work);
		return cab;
	}

	public SynchronizationPoint<IOException> onLoaded() {
		return loaded;
	}
	
	public List<File> getFiles() {
		return files;
	}
	
	private CabFile(IO.Readable io) {
		this.io = io;
	}
	
	private IO.Readable io;
	private int per_datablock_reserved_area_size;
	
	public void close() {
		try { io.close(); } catch (Throwable t) {}
	}

	private static class Folder {
		private long offset;
		private int blocks;
		private int compression;
		private CachedObject<IO.Readable.Seekable> io = null;
		private SynchronizationPoint<NoException> open = null;
	}
	public static class File {
		private long uncompressedSize;
		private long uncompressedOffset;
		private int folderIndex;
		private int date;
		private int time;
		private int attributes;
		private String name;
		
		public String getName() { return name; }
		public long getSize() { return uncompressedSize; }
		public long getDateTime() {
			Calendar c = Calendar.getInstance();
			c.set(Calendar.YEAR, (date >> 9) + 1980);
			c.set(Calendar.MONTH, ((date & 0x1E0) >> 5) - 1);
			c.set(Calendar.DAY_OF_MONTH, (date & 0x1F));
			c.set(Calendar.HOUR_OF_DAY, (time >> 11));
			c.set(Calendar.MINUTE, (time & 0x7E0) >> 5);
			c.set(Calendar.SECOND, (time & 0x1F) * 2);
			c.set(Calendar.MILLISECOND, 0);
			return c.getTimeInMillis();
		}
		public boolean isReadOnly() { return (attributes & 2) != 0; }
		public boolean isHidden() { return (attributes & 4) != 0; }
		public boolean isSystem() { return (attributes & 8) != 0; }
		public boolean isArchive() { return (attributes & 0x40) != 0; }
		public boolean isExecutable() { return (attributes & 0x80) != 0; }
		public boolean isNameEncodedInUTF() { return (attributes & 0x100) != 0; }
	}
	
	private SynchronizationPoint<IOException> loaded = new SynchronizationPoint<>();
	private ArrayList<Folder> folders;
	private ArrayList<File> files;
	
	@SuppressWarnings("resource")
	private void load(WorkProgress progress, long work) {
		// list of files is at the beginning of the file, we should have a buffered readable, but with small pre-buffered size
		IO.Readable.Buffered bio;
		if (io instanceof IO.Readable.Buffered)
			bio = (IO.Readable.Buffered)io;
		else
			bio = new SimpleBufferedReadable(io, 512);
		bio.canStartReading().listenAsync(new Load(bio, progress, work), true);
	}
	
	private class Load extends Task.Cpu<Void, NoException> {
		public Load(IO.Readable.Buffered io, WorkProgress progress, long work) {
			super("Load CAB file content", io.getPriority());
			this.io = io;
			this.progress = progress;
			this.work = work;
		}
		private IO.Readable.Buffered io;
		private WorkProgress progress;
		private long work;
		@Override
		public Void run() {
			try {
				io.skip(16);
				long first_cffile_entry = DataUtil.readUnsignedIntegerLittleEndian(io);
				io.skip(6);
				int nb_cffolders = DataUtil.readUnsignedShortLittleEndian(io);
				int nb_cffiles = DataUtil.readUnsignedShortLittleEndian(io);
				int flags = DataUtil.readUnsignedShortLittleEndian(io);
				//int set_id = IOUtil.readUnsignedShortIntel(tmp, 16);
				//int iCabinet = IOUtil.readUnsignedShortIntel(tmp, 18);
				io.skip(4);
				// TODO handle previous and next cabinet files
				long start = 36;
				int per_folder_reserved_area_size = 0;
				if ((flags & 4) != 0) {
					int per_cabinet_reserved_area_size = DataUtil.readUnsignedShortLittleEndian(io);
					per_folder_reserved_area_size = io.read();
					per_datablock_reserved_area_size = io.read();
					start += 4;
					io.skip(per_cabinet_reserved_area_size);
					start += per_cabinet_reserved_area_size;
				} else
					per_datablock_reserved_area_size = 0;
				if ((flags & 1) != 0) {
					String prev_cabinet = readNulString(io);
					start += prev_cabinet.length()+1;
					String prev_disk = readNulString(io);
					start += prev_disk.length()+1;
				}
				if ((flags & 2) != 0) {
					String next_cabinet = readNulString(io);
					start += next_cabinet.length()+1;
					String next_disk = readNulString(io);
					start += next_disk.length()+1;
				}
				if (progress != null) progress.progress(work/4);
				folders = new ArrayList<>(nb_cffolders);
				for (int i = 0; i < nb_cffolders; ++i) {
					Folder f = new Folder();
					f.offset = DataUtil.readUnsignedIntegerLittleEndian(io);
					f.blocks = DataUtil.readUnsignedShortLittleEndian(io);
					f.compression = DataUtil.readUnsignedShortLittleEndian(io);
					f.compression &= 0xF;
					folders.add(f);
					io.skip(per_folder_reserved_area_size);
					start += 8 + per_folder_reserved_area_size;
				}
				if (progress != null) progress.progress(work/4);
				work = work-2*(work/4);
				int nb = nb_cffiles;
				if (nb == 0 && progress != null) progress.progress(work);
				files = new ArrayList<>(nb_cffiles);
				io.skipSync(first_cffile_entry - start);
				for (int i = 0; i < nb_cffiles; ++i) {
					long step = work/nb--;
					work -= step;
					File f = new File();
					f.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(io);
					f.uncompressedOffset = DataUtil.readUnsignedIntegerLittleEndian(io);
					f.folderIndex = DataUtil.readUnsignedShortLittleEndian(io);
					f.date = DataUtil.readUnsignedShortLittleEndian(io);
					f.time = DataUtil.readUnsignedShortLittleEndian(io);
					f.attributes = DataUtil.readUnsignedShortLittleEndian(io);
					f.name = readNulString(io);
					files.add(f);
					if (progress != null) progress.progress(step);
				}
				loaded.unblock();
			} catch (IOException e) {
				loaded.error(e);
			}
			return null;
		}
	}
	
	private static String readNulString(IO.Readable.Buffered io) throws IOException {
		StringBuilder s = new StringBuilder();
		do {
			int c = io.read();
			if (c == -1) break;
			if (c == 0) break;
			s.append((char)c);
		} while (true);
		return s.toString();
	}
	
	public AsyncWork<IO.Readable, IOException> openFile(File file, byte priority) {
		AsyncWork<IO.Readable, IOException> result = new AsyncWork<>();
		new Task.Cpu<Void, NoException>("Open CAB inner file", priority) {
			@SuppressWarnings("resource")
			@Override
			public Void run() {
				Object folderUser = new Object();
				Folder folder = folders.get(file.folderIndex);
				SynchronizationPoint<NoException> sp = null;
				synchronized (folder) {
					if (folder.io == null) {
						if (folder.open != null)
							sp = folder.open;
						else
							folder.open = new SynchronizationPoint<>();
					}
				}
				Runnable onFolderOpen = new Runnable() {
					@Override
					public void run() {
						SubIO.Readable.Seekable io = new SubIO.Readable.Seekable(folder.io.get(), file.uncompressedOffset, file.uncompressedSize, file.name, false);
						io.addCloseListener(new Runnable() {
							@Override
							public void run() {
								folder.io.release(folderUser);
							}
						});
						result.unblockSuccess(io);
					}
				};
				if (folder.io != null) {
					onFolderOpen.run();
					return null;
				}
				if (sp != null) {
					sp.listenInline(onFolderOpen);
					return null;
				}
				CabFolderIO.Readable fio = new CabFolderIO.Readable((IO.Readable.Seekable)io, folder.offset, folder.blocks, per_datablock_reserved_area_size);
				IO.Readable.Seekable folderIO;
				switch (folder.compression) {
				case 0: // no compression
					folderIO = fio;
					break;
				case 1: // MSZIP
					try { folderIO = new ReadableToSeekable(new MSZipReadable(fio, priority), 32768); }
					catch (IOException e) {
						result.error(e);
						return null;
					}
					break;
				case 2: // QUANTUM
					// TODO
				case 3: // LZX
					// TODO
				default:
					fio.closeAsync();
					result.error(new IOException("Unsupported CAB compression " + folder.compression));
					return null;
				}
				folder.io = new CachedObject<IO.Readable.Seekable>(folderIO, 30 * 1000) {
					@Override
					protected void closeCachedObject(IO.Readable.Seekable io) {
						io.closeAsync();
					}
				};
				folder.io.use(folderUser);
				onFolderOpen.run();
				folder.open.unblock();
				return null;
			}
		}.start();
		return result;
	}
	
}
