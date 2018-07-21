package net.lecousin.dataformat.archive.cfb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;

public class CFBFile implements Closeable {

	public static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(CFBFile.class);
	}
	
	public static class CFBSubFile {
		String name;
		FragmentedRangeLong fragments;
		public String getName() { return name; }
	}
	
	public <T extends IO.Readable.Seekable&IO.KnownSize> CFBFile(T io, boolean load, WorkProgress progress, long work) throws IOException {
		if (io instanceof IO.Readable.Buffered)
			this.io = io;
		else
			this.io = new BufferedIO(io, io.getSizeSync(), 512, 4096, true);
		if (load)
			load(progress, work);
		else if (progress != null)
			progress.progress(work);
	}
	
	IO.Readable.Seekable io;
	private AsyncWork<Void,IOException> loaded = null;
	private ArrayList<CFBSubFile> content = new ArrayList<>();

	@SuppressWarnings("unused")
	private void load(WorkProgress progress, long work) {
		byte[] header = new byte[512];
		AsyncWork<Integer,IOException> readHeader = this.io.readFullyAsync(0, ByteBuffer.wrap(header));
		loaded = new AsyncWork<Void,IOException>();
		new Loader(readHeader, header, progress, work);
	}
	
	public ISynchronizationPoint<IOException> getSynchOnReady() {
		if (loaded == null) load(null, 0);
		return loaded;
	}
	
	public ArrayList<CFBSubFile> getContent() {
		return content;
	}
	
	@Override
	public void close() throws IOException {
		try { io.close(); }
		catch (Exception e) { throw IO.error(e); } 
		io = null;
		content = null;
		loaded = null;
	}

	private class Loader {
		public Loader(AsyncWork<Integer,IOException> readHeader, byte[] header, WorkProgress progress, long work) {
			this.header = header;
			this.progress = progress;
			this.work = work;
			logger = getLogger();
			readHeader.listenInline(new AsyncWorkListener<Integer, IOException>() {
				@Override
				public void ready(Integer result) {
					if (result.intValue() < 512) {
						loaded.unblockError(new IOException("Invalid CFB file: unable to read the 512 first bytes: only "+result));
						return;
					}
					if (progress != null) { progress.progress(work/10); Loader.this.work -= work/10; }
					new Init().start();
				}
				@Override
				public void error(IOException error) {
					loaded.unblockError(error);
				}
				@Override
				public void cancelled(CancelException event) {
					loaded.unblockCancel(event);
				}
			});
		}
		private byte[] header;
		private int sector_size;
		private byte[] sector_buf;
		private long mini_stream_cutoff;
		private int mini_sector_size;
		private CFB_FAT fat;
		private CFB_MiniFAT minifat;
		private CFB_DIFAT difat;
		private WorkProgress progress;
		private long work;
		private Logger logger;
		
		private class Init extends Task.Cpu<Void,NoException> {
			private Init() {
				super("Initializing CFB File", Task.PRIORITY_NORMAL);
			}
			@Override
			public Void run() {
				//int min_ver = DataUtil.readUnsignedShortIntel(header, 0x18);
				//int maj_ver = DataUtil.readUnsignedShortIntel(header, 0x1A);
				int sector_shift = DataUtil.readUnsignedShortLittleEndian(header, 0x1E);
				sector_size = 1 << sector_shift;
				sector_buf = new byte[sector_size];
				int mini_sector_shift = DataUtil.readUnsignedShortLittleEndian(header, 0x20);
				mini_sector_size = 1 << mini_sector_shift;
				//long nb_dir_sectors = DataUtil.readUnsignedIntegerIntel(header, 0x28);
				//long nb_fat_sectors = DataUtil.readUnsignedIntegerIntel(header, 0x2C);
				long first_dir_sector = DataUtil.readUnsignedIntegerLittleEndian(header, 0x30);
				mini_stream_cutoff = DataUtil.readUnsignedIntegerLittleEndian(header, 0x38);
				long mini_fat_sector = DataUtil.readUnsignedIntegerLittleEndian(header, 0x3C);
				long nb_mini_fat_sectors = DataUtil.readUnsignedIntegerLittleEndian(header, 0x40);
				long difat_sector = DataUtil.readUnsignedIntegerLittleEndian(header, 0x44);
				long nb_difat_sectors = DataUtil.readUnsignedIntegerLittleEndian(header, 0x48);
				difat = new CFB_DIFAT(header, difat_sector, nb_difat_sectors);
				fat = new CFB_FAT(difat/*, nb_fat_sectors*/);
				minifat = new CFB_MiniFAT(fat, mini_fat_sector, nb_mini_fat_sectors);
				if (progress != null) { progress.progress(work/10); work -= work/10; }
				readNextRootDirectorySector(first_dir_sector);
				return null;
			}
		};
		
		private void readNextRootDirectorySector(long sector) {
			AsyncWork<Long,IOException> nextAW = readSector(sector);
			nextAW.listenInline(new Runnable() {
				@Override
				public void run() {
					if (!nextAW.isSuccessful()) {
						if (nextAW.isCancelled()) loaded.unblockCancel(nextAW.getCancelEvent());
						else loaded.unblockError(nextAW.getError());
						return;
					}
					long next = nextAW.getResult().longValue();
					if (next == 0 || next == sector) {
						loaded.unblockError(new IOException("Invalid next sector in CFB File: "+next));
						return;
					}
					new ReadNextDirectoryEntry(0, next).start();
				}
			});
		}
		
		private class ReadNextDirectoryEntry extends Task.Cpu<Void,NoException> {
			private ReadNextDirectoryEntry(int entryIndex, long next) {
				super("Read CFB Directory entry", Task.PRIORITY_NORMAL);
				this.entryIndex = entryIndex;
				this.next = next;
			}
			private int entryIndex;
			private long next;
			@Override
			public Void run() {
				if (entryIndex >= sector_size/128) {
					if (next == CFB_FAT.END_OF_CHAIN) {
						if (progress != null) progress.progress(work);
						loaded.unblockSuccess(null);
						return null;
					}
					readNextRootDirectorySector(next);
					return null;
				}
				
				byte type = sector_buf[entryIndex*128 + 0x42];
				if (type == 0) {
					entryIndex++;
					return run();
				}
				int name_size = DataUtil.readUnsignedShortLittleEndian(sector_buf, entryIndex*128 + 0x40);
				if (name_size > 0x40) {
					if (logger.error())
						logger.error("Invalid file name size ("+name_size+"): maximum is 64, in Compound file "+io.getSourceDescription());
					name_size = 0x40;
				}
				if (name_size == 0) {
					loaded.unblockError(new IOException("Invalid file name size: 0. Stop reading this CFB file."));
					return null;
				}
				StringBuilder str = new StringBuilder();
				for (int in = 0; in < (name_size-2)/2; ++in) {
					int c = (sector_buf[entryIndex*128+in*2] & 0xFF) | ((sector_buf[entryIndex*128+in*2+1] & 0xFF) << 8);
					str.append((char)c);
				}
				String name = str.toString();
				long start_sector = DataUtil.readUnsignedIntegerLittleEndian(sector_buf, entryIndex*128 + 0x74);
				long size = DataUtil.readLongLittleEndian(sector_buf, entryIndex*128 + 0x78);
				
				switch (type) {
				case 0x05: // root storage
					if (minifat.nb_sectors <= 0) {
						readStreamObject(name, size, start_sector);
						return null;
					}
					readRootStorage(/*name, */start_sector, size);
					return null;
				case 0x02: // stream object
					readStreamObject(name, size, start_sector);
					return null;
				default:
					if (!"Root Entry".equals(name)) {
						CFBSubFile file = new CFBSubFile();
						file.name = name;
						file.fragments = new FragmentedRangeLong();
						content.add(file);
					}
					entryIndex++;
					if (progress != null) { progress.progress(work/10); work -= work/10; }
					return run();
				}
			}
			
			private void readRootStorage(/*String name, */long start_sector, long size) {
				FragmentedRangeLong frags = new FragmentedRangeLong();
				if (size <= 0) {
					minifat.set_stream(frags);
					new ReadNextDirectoryEntry(entryIndex+1, next).start();
					return;
				}
				long s = size > sector_size ? sector_size : size;
				MutableLong remainingSize = new MutableLong(size - s);
				long pos = (start_sector+1)*sector_size;
				frags.addRange(pos, pos+s-1);
				getNextSector(start_sector).listenInline(new AsyncWorkListener<Long, IOException>() {
					@Override
					public void ready(Long next_sector) {
						if (next_sector.longValue() == CFB_FAT.END_OF_CHAIN) {
							minifat.set_stream(frags);
							new ReadNextDirectoryEntry(entryIndex+1, next).start();
							return;
						}
						long s = remainingSize.get() > sector_size ? sector_size : remainingSize.get();
						remainingSize.sub(s);
						long pos = (next_sector.longValue()+1)*sector_size;
						frags.addRange(pos, pos+s-1);
						getNextSector(next_sector.longValue()).listenInline(this);
					}
					@Override
					public void error(IOException error) {
						loaded.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						loaded.unblockCancel(event);
					}
				});
			}
			
			private void readStreamObject(String name, long size, long start_sector) {
				FragmentedRangeLong fragments = new FragmentedRangeLong();
				AsyncWork<Void,NoException> read;
				if (size < mini_stream_cutoff)
					read = readFragmentsFromMiniFAT(start_sector, size, fragments);
				else
					read = readFragmentsFromFAT(start_sector, size, fragments);
				read.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!read.isSuccessful())
							return;
						if (!"Root Entry".equals(name)) {
							CFBSubFile file = new CFBSubFile();
							file.name = name;
							file.fragments = fragments;
							content.add(file);
						}
						new ReadNextDirectoryEntry(entryIndex+1, next).start();
					}
				});
			}
			
			private AsyncWork<Void,NoException> readFragmentsFromMiniFAT(long sector, long size, FragmentedRangeLong fragments) {
				AsyncWork<Void,NoException> result = new AsyncWork<Void,NoException>();
				if (size <= 0) {
					result.unblockSuccess(null);
					return result;
				}
				long s = size > mini_sector_size ? mini_sector_size : size;
				MutableLong remainingSize = new MutableLong(size - s);
				long pos = sector*mini_sector_size;
				minifat.to_absolute_pos(pos, pos+s-1, fragments);
				minifat.next_sector(sector).listenInline(new AsyncWorkListener<Long, IOException>() {
					@Override
					public void ready(Long next_sector) {
						if (next_sector.longValue() == CFB_FAT.END_OF_CHAIN) {
							result.unblockSuccess(null);
							return;
						}
						long s = remainingSize.get() > mini_sector_size ? mini_sector_size : remainingSize.get();
						remainingSize.sub(s);
						long pos = next_sector.longValue()*mini_sector_size;
						minifat.to_absolute_pos(pos, pos+s-1, fragments);
						minifat.next_sector(next_sector.longValue()).listenInline(this);
					}
					@Override
					public void error(IOException error) {
						loaded.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						loaded.unblockCancel(event);
					}
				});
				return result;
			}

			private AsyncWork<Void,NoException> readFragmentsFromFAT(long sector, long size, FragmentedRangeLong fragments) {
				if (size <= 0)
					return new AsyncWork<>(null,null);
				AsyncWork<Void,NoException> result = new AsyncWork<Void,NoException>();
				new ReadFragmentsFromFAT(sector, size, fragments, result).start();
				return result;
			}
			private class ReadFragmentsFromFAT extends Task.Cpu<Void,NoException> {
				private ReadFragmentsFromFAT(long nextSector, long size, FragmentedRangeLong fragments, AsyncWork<Void,NoException> result) {
					super("Read sectors from FAT in CFB file", Task.PRIORITY_NORMAL);
					this.nextSector = nextSector;
					this.size = size;
					this.result = result;
					this.fragments = fragments;
				}
				private long nextSector;
				private long size;
				private AsyncWork<Void,NoException> result;
				private FragmentedRangeLong fragments;
				@Override
				public Void run() {
					do {
						if (nextSector == CFB_FAT.END_OF_CHAIN) {
							result.unblockSuccess(null);
							return null;
						}
						long s = size > sector_size ? sector_size : size;
						size -= s;
						long pos = (nextSector+1)*sector_size;
						fragments.addRange(pos, pos+s-1);
						AsyncWork<Long,IOException> res = getNextSector(nextSector);
						if (!res.isUnblocked()) {
							res.listenInline(new AsyncWorkListener<Long, IOException>() {
								@Override
								public void ready(Long r) {
									new ReadFragmentsFromFAT(r.longValue(), size, fragments, result).start();
								}
								@Override
								public void error(IOException error) {
									loaded.unblockError(error);
								}
								@Override
								public void cancelled(CancelException event) {
									loaded.unblockCancel(event);
								}
							});
							return null;
						}
						if (!res.isSuccessful()) {
							loaded.unblockError(res.getError());
							return null;
						}
						nextSector = res.getResult().longValue();
					} while (true);
				}
			}	
		}
		
		private AsyncWork<Long,IOException> readSector(long sector) {
			AsyncWork<Long,IOException> result = new AsyncWork<Long,IOException>();
			io.readFullyAsync((sector+1) * sector_size, ByteBuffer.wrap(sector_buf)).listenInline(new AsyncWorkListener<Integer, IOException>() {
				@Override
				public void ready(Integer r) {
					if (r.intValue() <= 0) {
						result.unblockError(new IOException("Unable to read sector "+sector));
						return;
					}
					fat.next(sector).listenInline(result);
				}
				@Override
				public void error(IOException error) {
					result.unblockError(new IOException("Unable to read sector "+sector, error));
				}
				@Override
				public void cancelled(CancelException event) {
					result.unblockCancel(event);
				}
			});
			return result;
		}
		
		private AsyncWork<Long,IOException> getNextSector(long sector) {
			return fat.next(sector);
		}


		private class CFB_DIFAT {
			public CFB_DIFAT(byte[] buf, long first_sector, long nb_sectors) {
				difat = new long[109];
				for (int i = 0; i < 109; ++i)
					difat[i] = DataUtil.readUnsignedIntegerLittleEndian(buf, 0x4C + i*4);
				this.next_sector = first_sector;
				this.nb_sectors = nb_sectors;
				this.buf = new byte[sector_size];
			}
			
			private long pos = 0;
			private long[] difat;
			private long next_sector;
			private long nb_sectors;
			private byte[] buf;
			
			AsyncWork<Long,IOException> get(int sector) {
				if (sector < 109 + pos * (sector_size/4-1))
					return new AsyncWork<>(new Long(difat[sector]),null);
				if (pos == nb_sectors)
					return new AsyncWork<>(null,new IOException("Invalid sector number: "+sector));
				AsyncWork<Long,IOException> result = new AsyncWork<Long,IOException>();
				readNextSector().listenInline(new AsyncWorkListener<Void, IOException>() {
					@Override
					public void ready(Void r) {
						if (sector < 109 + pos * (sector_size/4-1)) {
							result.unblockSuccess(new Long(difat[sector]));
							return;
						}
						if (pos == nb_sectors) {
							result.unblockError(new IOException("Invalid sector number: "+sector));
							return;
						}
						readNextSector().listenInline(this);
					}
					@Override
					public void error(IOException error) {
						result.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						result.unblockCancel(event);
					}
				});
				return result;
			}
			
			private AsyncWork<Void,IOException> readNextSector() {
				AsyncWork<Void,IOException> result = new AsyncWork<Void,IOException>();
				AsyncWork<Integer,IOException> read = io.readFullyAsync((next_sector+1)*sector_size, ByteBuffer.wrap(buf));
				read.listenAsync(new Task.Cpu<Void,NoException>("Reading CFB DIFAT section",Task.PRIORITY_NORMAL) {
					@Override
					public Void run() {
						if (!read.isSuccessful()) {
							result.unblockError(read.getError());
							return null;
						}
						if (read.getResult().intValue() != sector_size) {
							result.unblockError(new IOException("Unable to read DIFAT sector "+pos));
							return null;
						}
						long[] new_difat = new long[difat.length + sector_size/4 - 1];
						int start = difat.length;
						for (int i = 0; i < start; ++i) new_difat[i] = difat[i];
						difat = new_difat;
						for (int i = 0; i < sector_size/4-1; ++i)
							difat[i + start] = DataUtil.readUnsignedIntegerLittleEndian(buf, i*4);
						next_sector = DataUtil.readUnsignedIntegerLittleEndian(buf, sector_size-4);
						pos++;
						result.unblockSuccess(null);
						return null;
					}
				}, true);
				return result;
			}
			
		}
		
		private class CFB_MiniFAT {
			public CFB_MiniFAT(CFB_FAT fat, long sector, long nb_sectors) {
				this.first_sector = sector;
				this.nb_sectors = nb_sectors;
				this.fat = fat;
				this.buf = new byte[sector_size];
				this.buf_pos = -1;
			}
			
			public long first_sector;
			public long nb_sectors;
			private CFB_FAT fat;
			private FragmentedRangeLong frags;
			private byte[] buf;
			private long buf_pos;
			
			public void set_stream(FragmentedRangeLong frags) {
				this.frags = frags;
			}
			
			public AsyncWork<Long,IOException> next_sector(long sector) {
				long off = sector*4;
				long fat_sector = first_sector;
				AsyncWork<Long,IOException> result = new AsyncWork<Long,IOException>();
				if (off >= sector_size) {
					MutableLong o = new MutableLong(off);
					fat.next(fat_sector).listenInline(new AsyncWorkListener<Long,IOException>() {
						@Override
						public void ready(Long fat_sector) {
							o.sub(sector_size);
							if (o.get() >= sector_size) {
								fat.next(fat_sector.longValue()).listenInline(this);
								return;
							}
							next_sector2(o.get(), fat_sector.longValue(), result);
						}
						@Override
						public void error(IOException error) {
							result.unblockError(error);
						}
						@Override
						public void cancelled(CancelException event) {
							result.unblockCancel(event);
						}
					});
					return result;
				}
				next_sector2(off, fat_sector, result);
				return result;
			}
			private void next_sector2(long off, long fat_sector, AsyncWork<Long,IOException> result) {
				if (buf_pos == fat_sector) {
					result.unblockSuccess(new Long(DataUtil.readUnsignedIntegerLittleEndian(buf, (int)off)));
					return;
				}
				io.readFullyAsync((fat_sector+1)*sector_size, ByteBuffer.wrap(buf)).listenInline(new AsyncWorkListener<Integer, IOException>() {
					@Override
					public void ready(Integer r) {
						buf_pos = fat_sector;
						result.unblockSuccess(new Long(DataUtil.readUnsignedIntegerLittleEndian(buf, (int)off)));
					}
					@Override
					public void error(IOException error) {
						result.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						result.unblockCancel(event);
					}
				});
			}
			
			public void to_absolute_pos(long pos, long end, FragmentedRangeLong to) {
				long i = 0;
				for (RangeLong r : frags) {
					if (pos >= i+r.max-r.min+1) { // start after the current range
						i += r.max-r.min+1;
						continue;
					}
					long min = r.min+pos-i;
					long max = r.min+end-i;
					if (max <= r.max) {
						to.addRange(min, max);
						break;
					}
					to.addRange(min, r.max);
					pos += r.max-(r.min+pos-i)+1;
				}
			}
			
		}
		
		private class CFB_FAT {
			public CFB_FAT(CFB_DIFAT difat/*, long nb_sectors*/) {
				this.difat = difat;
				//this.nb_sectors = nb_sectors;
				this.buf = new byte[sector_size];
				this.buf_pos = -1;
			}
			
			private CFB_DIFAT difat;
			//private long nb_sectors;
			private byte[] buf;
			private int buf_pos;
			
			public static final long END_OF_CHAIN = 0xFFFFFFFEL;
			
			AsyncWork<Long,IOException> next(long sector) {
				int difat_sec = (int)(sector/(sector_size/4));
				if (buf_pos == difat_sec)
					return new AsyncWork<>(new Long(DataUtil.readUnsignedIntegerLittleEndian(buf, (int)((sector%(sector_size/4))*4))), null);
				
				AsyncWork<Long,IOException> result = new AsyncWork<Long,IOException>();
				difat.get(difat_sec).listenInline(new AsyncWorkListener<Long, IOException>() {
					@Override
					public void ready(Long r) {
						long pos = (r.longValue()+1)*sector_size;
						io.readFullyAsync(pos, ByteBuffer.wrap(buf)).listenInline(new AsyncWorkListener<Integer, IOException>() {
							@Override
							public void ready(Integer r) {
								if (r.intValue() != sector_size) {
									result.unblockError(new IOException("Unable to read FAT sector at "+pos+" (difact sector "+difat_sec+", sector "+sector+")"));
									return;
								}
								buf_pos = difat_sec;
								result.unblockSuccess(new Long(DataUtil.readUnsignedIntegerLittleEndian(buf, (int)((sector%(sector_size/4))*4))));
							}
							@Override
							public void error(IOException error) {
								result.unblockError(error);
							}
							@Override
							public void cancelled(CancelException event) {
								result.unblockCancel(event);
							}
						});
					}
					@Override
					public void error(IOException error) {
						result.unblockError(new IOException("Invalid sector number "+sector+": unable to get it from DIFAT", error));
					}
					@Override
					public void cancelled(CancelException event) {
						result.unblockCancel(event);
					}
				});
				return result;
			}

		}
	}

}
