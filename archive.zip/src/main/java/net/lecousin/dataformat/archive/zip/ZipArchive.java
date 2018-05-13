package net.lecousin.dataformat.archive.zip;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import net.lecousin.compression.deflate.DeflateReadable;
import net.lecousin.dataformat.archive.zip.ZipArchiveRecords.EndOfCentralDirectory;
import net.lecousin.dataformat.archive.zip.ZipArchiveScanner.EntryListener;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.provider.IOProvider;
import net.lecousin.framework.log.Logger;

public class ZipArchive implements Closeable {
	
	static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(ZipArchive.class);
	}
	
	// format spec: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
	// version implemented: 6.3.4

	public static ZipArchive loadForInformation(IO.Readable in) {
		return new ZipArchive(in);
	}
	public static ZipArchive loadForExtraction(IO.Readable in, IOProvider.Readable ioProvider) {
		return new ZipArchive(in, ioProvider);
	}
	public static <T extends IO.Readable.Seekable&IO.KnownSize> ZipArchive loadForExtraction(T in) {
		return new ZipArchive(in);
	}
	public static <T extends IO.Readable.Seekable&IO.KnownSize> ZipArchive loadForExtraction(T in, IOProvider.Readable.Seekable.DeterminedSize ioProvider) {
		return new ZipArchive(in, ioProvider);
	}
	/* TODO
	public static <T extends IO.Readable.Seekable & IO.Writable.Seekable & IO.DeterminedSize & IO.Resizable & IO.Readable.Buffered & IO.Writable.Buffered> ZipArchive loadForModification(T io) {
		
	}*/
	
	public static interface ScanListener {
		public boolean fileFound(ZippedFile file);
		public void read(ZippedFile file, IO.Readable input);
		public void endOfScan();
		public void unableToUncompress(ZippedFile file, IOException error);
		public void error(IOException error);
	}
	public static void scanForExtraction(IO.Readable input, ScanListener listener) {
		ArrayList<ZippedFile> entriesToUncompress = new ArrayList<ZippedFile>();
		ZipArchiveScanner scanner = new ZipArchiveScanner(input, new ZipArchiveScanner.EntryListener() {
			@Override
			public Listener<IO.Readable> entryFound(ZippedFile entry, long positionInZip) {
				if (!listener.fileFound(entry))
					return null;
				return new Listener<IO.Readable>() {
					@Override
					public void fire(IO.Readable uncompressed) {
						if (uncompressed == null) {
							if (!(input instanceof IO.Readable.Seekable)) {
								listener.unableToUncompress(entry, new IOException("The zipped file cannot be read without seeking capability"));
								return;
							}
							entriesToUncompress.add(entry);
							return;
						}
						listener.read(entry, uncompressed);
					}
				};
			}
			@Override
			public void centralDirectoryEntryFound(ZippedFile entry) {
				ZippedFile local = null;
				for (ZippedFile f : entriesToUncompress)
					if (f.filename.equals(entry.filename)) {
						local = f;
						break;
					}
				if (local == null)
					return;
				long pos;
				try {
					pos = ((IO.Readable.Seekable)input).getPosition();
				} catch (IOException e) {
					listener.unableToUncompress(entry, e);
					return;
				}
				try {
					((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, local.offset + local.headerLength);
					listener.read(entry, entry.uncompressInline(input));
				} catch (IOException e) {
					listener.unableToUncompress(entry, e);
				}
				try {
					((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, pos);
				} catch (IOException e) {
				}
			}
			@Override
			public void endOfCentralDirectoryFound(EndOfCentralDirectory end) {
			}
		});
		scanner.scan().listenInline(
			() -> {
				listener.endOfScan();
			},
			(error) -> {
				listener.error(error);
			},
			(cancel) -> {
				listener.endOfScan();
			}
		);
	}
	
	private ZipArchive(IO.Readable io) {
		this.io = io;
		ZipArchiveScanner scan = new ZipArchiveScanner(this.io, new EntryListener() {
			@Override
			public Listener<IO.Readable> entryFound(ZippedFile entry, long positionInZip) {
				localEntries.put(new Long(positionInZip), entry);
				return null;
			}
			@Override
			public void centralDirectoryEntryFound(ZippedFile entry) {
				centralDirectory.add(entry);
			}
			@Override
			public void endOfCentralDirectoryFound(EndOfCentralDirectory end) {
				endOfCentralDirectory = end;
			}
		});
		loaded = scan.scan();
		loaded.listenInline(new Runnable() {
			@Override
			public void run() {
				ZipArchive.this.io.closeAsync();
				ZipArchive.this.io = null;
			}
		});
	}
	private ZipArchive(IO.Readable io, IOProvider.Readable ioProvider) {
		this(io);
		this.ioProvider = ioProvider;
	}
	private ZipArchive(IO.Readable.Seekable io) {
		this.io = io;
		if (io instanceof IO.Readable.Buffered)
			this.ioSeekBuf = (IO.Readable.Buffered)io;
		ZipArchiveExtractor open = new ZipArchiveExtractor(this);
		loaded = new SynchronizationPoint<>();
		open.done.listenInline(new Runnable() {
			@Override
			public void run() {
				if (open.done.isSuccessful()) {
					((SynchronizationPoint<IOException>)loaded).unblock();
					return;
				}
				if (open.done.getError() instanceof IOException) {
					((SynchronizationPoint<IOException>)loaded).error((IOException)open.done.getError());
					return;
				}
				if (logger.debug())
					logger.debug("Unable to read central directory (" + open.done.getError().getMessage() + "), start scanning zip file " + io.getSourceDescription());
				// extractor failed, we can try the scanner
				AsyncWork<Long,IOException> restart = io.seekAsync(SeekType.FROM_BEGINNING, 0);
				ZipArchiveScanner scan = new ZipArchiveScanner(io, new EntryListener() {
					@Override
					public Listener<IO.Readable> entryFound(ZippedFile entry, long positionInZip) {
						localEntries.put(new Long(positionInZip), entry);
						return null;
					}
					@Override
					public void centralDirectoryEntryFound(ZippedFile entry) {
						centralDirectory.add(entry);
					}
					@Override
					public void endOfCentralDirectoryFound(EndOfCentralDirectory end) {
						endOfCentralDirectory = end;
					}
				});
				restart.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP file", io.getPriority(), () -> {
					scan.scan().listenInline((SynchronizationPoint<IOException>)loaded);
				}), loaded);
			}
		});
	}
	private ZipArchive(IO.Readable.Seekable io, IOProvider.Readable.Seekable ioProvider) {
		this(io);
		this.ioProvider = ioProvider;
	}
	
	IO.Readable io;
	IO.Readable.Buffered ioSeekBuf;
	IOProvider.Readable ioProvider;
	private ISynchronizationPoint<IOException> loaded;
	EndOfCentralDirectory endOfCentralDirectory;
	ArrayList<ZippedFile> centralDirectory = new ArrayList<>();
	HashMap<Long,ZippedFile> localEntries = new HashMap<>();
	Logger logger = getLogger();
	
	public ISynchronizationPoint<IOException> getSynchOnReady() {
		return loaded;
	}
	
	public Collection<ZippedFile> getZippedFiles() {
		if (endOfCentralDirectory != null && !centralDirectory.isEmpty())
			return centralDirectory;
		if (!localEntries.isEmpty())
			return localEntries.values();
		return centralDirectory;
	}
	
	@Override
	public void close() throws IOException {
		if (io != null) 
			try { io.close(); }
			catch (Exception e) { throw IO.error(e); }
		io = null;
		if (ioSeekBuf != null)
			try { ioSeekBuf.close(); }
			catch (Exception e) { throw IO.error(e); }
		ioSeekBuf = null;
		ioProvider = null;
		loaded = null;
		centralDirectory = null;
		endOfCentralDirectory = null;
		localEntries = null;
	}
	
	public static class ZippedFile {
		int versionMadeBy;
		int versionNeeded;
		int flags;
		int compressionMethod;
		long lastModificationTimestamp;
		long lastAccessTimestamp;
		long creationTimestamp;
		int userID = -1, groupID = -1;
		long crc32;
		long compressedSize;
		long uncompressedSize;
		String filename;
		String comment;
		long offset;
		long diskNumberStart;
		
		int headerLength;
		
		ZippedFile localEntry = null;
		
		public String getFilename() { return filename; }
		public String getComment() { return comment; }
		public long getCompressedSize() { return compressedSize; }
		public long getUncompressedSize() { return uncompressedSize; }
		
		public boolean isEcrypted() { return (flags&1) != 0; }
		
		@SuppressWarnings("resource")
		public AsyncWork<IO.Readable,IOException> uncompress(ZipArchive zip, byte priority) {
			if (localEntry != null)
				return uncompress2(zip, priority, null);
			AsyncWork<IO.Readable,IOException> sp = new AsyncWork<>();
			AsyncWork<Long,IOException> skip = null;
			// first, we need to skip the Local File Header
			IO.Readable.Buffered input = null;
			IO.Readable opened = null;
			try {
				if (zip.io != null) {
					synchronized (zip) {
						if (zip.ioSeekBuf == null) {
							zip.ioSeekBuf = new BufferedIO.ReadOnly((IO.Readable.Seekable)zip.io, 8192, ((IO.KnownSize)zip.io).getSizeSync());
						}
					}
					input = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)zip.ioSeekBuf, offset, ((IO.KnownSize)zip.io).getSizeSync()-offset, zip.io.getSourceDescription()+'/'+filename+" [local header]", false);
				} else if (zip.ioProvider != null) {
					opened = zip.ioProvider.provideIOReadable(priority);
					input = new PreBufferedReadable(opened, 512, priority, 8192, priority, 10);
					if (offset > 0)
						skip = input.skipAsync(offset);
				} else
					throw new IOException("Zip must be open for extraction");
			} catch (IOException e) {
				sp.unblockError(e);
				return sp;
			}
			IO.Readable.Buffered i = input;
			IO.Readable o = opened;
			AsyncWork<Long,IOException> s = skip;
			Task<Void,NoException> task = new Task.Cpu<Void, NoException>("Read zip local entry", priority) {
				@Override
				public Void run() {
					if (s != null && s.hasError()) {
						if (o != null) try { o.close(); } catch (Throwable t) {}
						sp.unblockError(s.getError());
						return null;
					}
					AsyncWork<ZippedFile, IOException> readEntry = ZipArchiveRecords.readLocalFileEntry(i, false, offset);
					readEntry.listenAsync(new Task.Cpu.FromRunnable("Uncompress zip entry", priority, () -> {
						if (readEntry.hasError()) {
							if (o != null) try { o.close(); } catch (Throwable t) {}
							sp.unblockError(readEntry.getError());
							return;
						}
						localEntry = readEntry.getResult();
						uncompress2(zip, priority, i).listenInline(new AsyncWorkListener<IO.Readable, IOException>() {
							@Override
							public void ready(Readable result) {
								sp.unblockSuccess(result);
							}
							@Override
							public void error(IOException error) {
								if (o != null) o.closeAsync();
								sp.unblockError(error);
							}
							@Override
							public void cancelled(CancelException event) {
								if (o != null) o.closeAsync();
								sp.unblockCancel(event);
							}
						});
					}), true);
					return null;
				}
			};
			if (skip == null)
				input.canStartReading().listenAsync(task, true);
			else
				skip.listenAsync(task, true);
			return sp;
		}
		
		@SuppressWarnings("resource")
		private AsyncWork<IO.Readable,IOException> uncompress2(ZipArchive zip, byte priority, IO.Readable.Buffered input) {
			if (zip.logger.debug())
				zip.logger.debug("Start unzipping entry " + filename + " from " + localEntry.offset + " + " + localEntry.headerLength);
			// compressed stream
			AsyncWork<Long,IOException> skip = null;
			IO.Readable uncompressed;
			IO.Readable opened = null;
			try {
				IO.Readable content;
				if (zip.io != null) {
					content = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)zip.ioSeekBuf, localEntry.offset + localEntry.headerLength, compressedSize, zip.io.getSourceDescription()+'/'+filename, false);
				} else if (zip.ioProvider != null) {
					if (input == null) {
						opened = zip.ioProvider.provideIOReadable(priority);
						input = new PreBufferedReadable(opened, 512, priority, 8192, priority, 10);
						skip = input.skipAsync(localEntry.offset + localEntry.headerLength);
					}
					content = new SubIO.Readable(input, compressedSize, zip.ioProvider.getDescription()+'/'+filename, true);
				} else
					throw new IOException("Zip must be open for extraction");
				try {
					uncompressed = getUncompressedStream(content, priority, uncompressedSize);
				} catch (IOException e) {
					try { content.close(); }
					catch (Exception e2) { throw IO.error(e2); }
					throw e;
				}
			} catch (IOException e) {
				if (opened != null) try { opened.close(); } catch (Throwable t) {}
				return new AsyncWork<>(null, e);
			}
			if (skip == null || skip.isUnblocked())
				return new AsyncWork<IO.Readable, IOException>(uncompressed, null);
			AsyncWork<IO.Readable, IOException> sp = new AsyncWork<>();
			skip.listenInline(new Runnable() {
				@Override
				public void run() {
					sp.unblockSuccess(uncompressed);
				}
			});
			return sp;
		}
		
		private IO.Readable getUncompressedStream(IO.Readable content, byte priority, long size) throws IOException {
			switch (compressionMethod) {
			case 0: // stored (no compression)
				return content;
			case 8: // Deflated
				return new DeflateReadable.SizeKnown(content, priority, size, true);
			default:
				throw new IOException("Unsupported compression method in Zip: "+compressionMethod);
			}
		}
		
		@SuppressWarnings("resource")
		IO.Readable uncompressInline(IO.Readable input) throws IOException {
			return getUncompressedStream(new SubIO.Readable(input, compressedSize, "Zipped file "+filename, false), Task.PRIORITY_NORMAL, uncompressedSize);
		}
	}
	
	/*
	public static class FileToZip {
		public IO.Readable stream;
		public String name;
		public int compressionLevel = Deflater.BEST_COMPRESSION;
	}
	
	public void modify(Collection<ZippedFile> toRemove, Collection<FileToZip> toAdd, byte priority) {
		// launch compression in background
		//  - up to 32*64K free buffers = 2MB
		//  - launch up to 50 compressions of up to 1MB = up to 50MB
		Buffers buffers = new Buffers(65536, 32);
		LinkedList<FileToZip> toBeAdded = new LinkedList<>(toAdd);
		ZipCompressor.Listener compressions = new ZipCompressor.Listener();
		int count = 0;
		while (!toBeAdded.isEmpty() && count < 50) {
			FileToZip file = toBeAdded.removeFirst();
			// start to compress up to 1MB
			ZipCompressor comp = new ZipCompressor(file, priority, 16, buffers, compressions);
			comp.start();
			count++;
		}
		// first, we remove the files
		FragmentedRangeLong used = new FragmentedRangeLong();
		for (ZippedFile file : new ArrayList<>(getZippedFiles())) {
			if (toRemove != null && toRemove.contains(file)) {
				if (centralDirectory != null) centralDirectory.remove(file);
				localEntries.remove(new Long(file.offset));
				continue;
			}
			if (file.localEntry == null)
				file.localEntry = ZipArchiveRecords.readLocalFileEntry((IO.Readable.Buffered)io, false, file.offset);
			long size = file.headerLength;
			// TODO here we may have encryption header between local header and data
			size += file.compressedSize;
			// add data descriptor if present
			if ((file.localEntry.flags & 8) != 0)
				size += 16; // TODO +8 bytes if zip64
			used.add(new RangeLong(file.offset, file.offset+size-1));
		}
		// unfragment the file
		long freeOffset = 0;
		if (!used.isEmpty()) {
			long toMove = used.get_min();
			RangeLong next = used.removeFirst();
			do {
				if (toMove > 0)
					IOUtil.move((IO.Readable.Seekable&IO.Writable.Seekable)io, next.min, next.min - toMove, next.max - next.min + 1);
				if (used.isEmpty()) {
					freeOffset = next.max - toMove + 1;
					break;
				}
				RangeLong r = used.removeFirst();
				toMove += r.min - next.max - 1;
				next = r;
			} while (true);
		}
		// then, we can add the files
		if (!toAdd.isEmpty()) {
			do {
				ZipCompressor comp = null;
				SynchronizationPoint sp = null;
				synchronized (compressions) {
					// first we check the ones completely compressed and ready to be written, so we can launch another one in background
					if (!compressions.readyToBeWritten.isEmpty())
						comp = compressions.readyToBeWritten.removeFirst();
					// if nothing ready, we may have a large file which already reached the 1MB of compressed data
					else if (!compressions.maxOutputsReached.isEmpty())
						comp = compressions.maxOutputsReached.removeFirst();
					// check if we have errors
					else if (!compressions.inError.isEmpty())
						comp = compressions.inError.removeFirst();
					// nothing ready, check if something is still in progress
					else if (compressions.inProgress.isEmpty()) {
						// no more things to do
						break;
					// nothing ready but something in progress
					} else {
						// try to find one with at least one full buffer ready to be written
						for (ZipCompressor c : compressions.inProgress) {
							if (c.outputs.isEmpty()) continue;
							synchronized (c.outputs) {
								if (c.outputs.size() > 1 || c.outputs.get(0).remaining() == 0) {
									comp = c;
									break;
								}
							}
						}
						if (comp == null)
							sp = compressions.sp;
					}
				}
				if (sp != null) {
					sp.block(0);
					continue;
				}
				if (comp.endReached) {
					// ready to be written
					byte[] header = ZipArchiveRecords.prepareLocalFileEntry(comp.file.name, comp.compressedSize, comp.uncompressedSize, comp.crc32.getValue());
					IO.Writable.Seekable io = (IO.Writable.Seekable)this.io;
					ArrayList<AsyncWork<?,IOException>> writes = new ArrayList<>(1+comp.outputs.size());
					writes.add(io.writeAsync(freeOffset, ByteBuffer.wrap(header)));
					freeOffset += header.length;
					for (ByteBuffer buf : comp.outputs) {
						buf.flip();
						int nb = buf.remaining();
						AsyncWork<Integer,IOException> write = io.writeAsync(freeOffset, buf);
						write.listenInline(new RunnableWithData<ByteBuffer>(buf) {
							@Override
							public void run() {
								buffers.freeBuffer(getData());
							}
						});
						writes.add(write);
						freeOffset += nb;
					}
					Threading.waitUnblockedWithError(writes);
					// TODO add central directory entry
				} else if (comp.error != null) {
					// TODO
					// TODO free buffers
				} else {
					byte[] header = ZipArchiveRecords.prepareLocalFileEntry(comp.file.name, comp.compressedSize, comp.uncompressedSize, comp.crc32.getValue());
					IO.Writable.Seekable io = (IO.Writable.Seekable)this.io;
					io.writeAsync(freeOffset, ByteBuffer.wrap(header)); // TODO
					// TODO start to write, and continue compression
					// TODO free buffers
				}
				// launch another one in background
				if (!toBeAdded.isEmpty()) {
					FileToZip file = toBeAdded.removeFirst();
					// start to compress up to 1MB
					comp = new ZipCompressor(file, priority, 16, buffers, compressions);
					comp.start();
				}
			} while (true);
			// TODO end: central directory...
		}
	}
	*/
}
