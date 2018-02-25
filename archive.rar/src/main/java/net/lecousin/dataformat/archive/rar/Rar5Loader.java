package net.lecousin.dataformat.archive.rar;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import net.lecousin.dataformat.archive.rar.RarArchive.RARFile;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.progress.WorkProgress;

class Rar5Loader extends RarLoader {

	Rar5Loader(RarArchive rar, WorkProgress progress, long work) {
		super(rar);
		this.progress = progress;
		this.work = work;
	}
	
	private WorkProgress progress;
	private long work;
	
	@Override
	protected void start() {
		SynchronizationPoint<IOException> canStart = new SynchronizationPoint<>();		
		if (!(rar.io instanceof IO.Readable.Buffered)) {
			// RAR 5 format uses variable length fields, so we need to have buffering
			AsyncWorkListener<Long, IOException> listener = new AsyncWorkListener<Long, IOException>() {
				@Override
				public void ready(Long result) {
					try { rar.io = new BufferedIO.ReadOnly(rar.io, 4096, result.longValue()); }
					catch (IOException e) {
						canStart.error(e);
						return;
					}
					((BufferedIO.ReadOnly)rar.io).canStartReading().listenInline(canStart);
				}
				@Override
				public void cancelled(CancelException event) {
					canStart.cancel(event);
				}
				@Override
				public void error(IOException error) {
					canStart.error(error);
				}
			};
			if (rar.io instanceof IO.KnownSize)
				((IO.KnownSize)rar.io).getSizeAsync().listenInline(listener);
			else
				rar.io.seekAsync(SeekType.FROM_END, 0).listenInline(listener);
		} else
			((IO.Readable.Buffered)rar.io).canStartReading().listenInline(canStart);
		canStart.listenAsync(new Task.Cpu<Void,NoException>("Start loading RAR 5 archive", rar.io.getPriority()) {
			@Override
			public Void run() {
				if (canStart.isCancelled()) {
					rar.contentLoaded.cancel(canStart.getCancelEvent());
					return null;
				}
				if (canStart.hasError()) {
					RarArchive.logger.error("Error initializing RAR5 loader", canStart.getError());
					rar.contentLoaded.error(canStart.getError());
					return null;
				}
				byte[] b = new byte[16384];
				readBlock((IO.Readable.Seekable&IO.Readable.Buffered)rar.io, 8, b, ByteBuffer.wrap(b), progress, work);
				return null;
			}
		}, true);
	}
	
	private static class BlockHeader {
		private long headerSize;
		private long headerType;
		private long headerFlags;
		private long extraSize;
		private long dataSize;
		private long dataOffset;
	}
	
	private static <T extends IO.Readable.Seekable&IO.Readable.Buffered> BlockHeader readBlockHeader(T io, long pos) throws IOException {
		BlockHeader h = new BlockHeader();
		io.seekSync(SeekType.FROM_BEGINNING, pos+4);
		h.headerSize = readVInt(io);
		h.dataOffset = io.getPosition()+h.headerSize;
		h.headerType = readVInt(io);
		h.headerFlags = readVInt(io);
		h.extraSize = ((h.headerFlags & 0x01) != 0 ? readVInt(io) : 0);
		h.dataSize = ((h.headerFlags & 0x02) != 0 ? readVInt(io) : 0);
		return h;
	}
	
	private <T extends IO.Readable.Seekable&IO.Readable.Buffered> void readBlock(T io, long pos, byte[] b, ByteBuffer bb, WorkProgress progress, long work) {
		try {
			BlockHeader h = readBlockHeader(io, pos);
			if (h.headerType == 1) {
				// Main Archive Header
				long flags = readVInt(io);
				rar.isVolumeArchive = (flags & 1) != 0;
				rar.isSolid = (flags & 4) != 0;
				rar.isLocked = (flags & 16) != 0;
				if ((flags & 2) != 0) {
					readVInt(io); // volume number
				}
				if ((h.headerFlags & 1) != 0) {
					// extra data present
					long endOfExtra = io.getPosition()+h.extraSize;
					while (io.getPosition() < endOfExtra) {
						long size = readVInt(io);
						long endOfRecord = io.getPosition()+size;
						long type = readVInt(io);
						if (type != 1) {
							// unknown, go to next record
							io.seekSync(SeekType.FROM_BEGINNING, endOfRecord);
							continue;
						}
						// locator record
						long locatorFlags = readVInt(io);
						if ((locatorFlags & 1) == 0) {
							// no quick open locator, we don't continue
							readBlock(io, endOfExtra, b, bb, progress, work);
							return;
						}
						long offset = readVInt(io);
						if (offset == 0) {
							readBlock(io, endOfExtra, b, bb, progress, work);
							return;
						}
						// we have a quick open locator, let's use it
						// TODO async because here we seek almost at the end of the file
						BlockHeader bh = readBlockHeader(io, pos+offset);
						if (bh.headerType == 3) {
							long headerOffset = io.getPosition();
							FileOrServiceHeaderPart sh = readFileOrServiceHeaderPart(io, b, bb);
							if (sh.name.equals("QO")) {
								if (progress != null) progress.progress(work/8);
								readQuickOpen(io, bh.dataOffset, bh.dataSize, headerOffset, b, bb, progress, work-work/8);
								return;
							}
						}
					}
					readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work);
					return;
				}
				readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work);
				return;
			}
			if (h.headerType == 2) {
				// file header
				FileOrServiceHeaderPart fh = readFileOrServiceHeaderPart(io, b, bb);
				RARFile file = rar.new RARFile();
				file.compressedSize = h.dataSize;
				file.uncompressedSize = fh.unpackedSize;
				file.method = fh.compressionMethod;
				file.name = fh.name;
				// TODO
				rar.content.add(file);
				if (progress != null) progress.progress(work/10);
				readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work-work/10);
				return;
			}
			if (h.headerType == 3) {
				// service header
				// TODO
				readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work);
				return;
			}
			if (h.headerType == 4) {
				// archive encryption header
				// TODO
				readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work);
				return;
			}
			if (h.headerType == 5) {
				// end of archive header
				if (progress != null) progress.progress(work);
				rar.contentLoaded.unblock();
				return;
			}
			// TODO unknown
			readBlock(io, h.dataOffset+h.dataSize, b, bb, progress, work);
		} catch (EOFException e) {
			RarArchive.logger.error("Unexpected end of file while reading RAR 5 block at "+pos, e);
			rar.contentLoaded.error(new IOException("Unexpected end of file"));
			return;
		} catch (IOException e) {
			RarArchive.logger.error("Error reading RAR 5 block at "+pos, e);
			rar.contentLoaded.error(e);
			return;
		}
	}
	
	// TODO this should return a structure with information
	// when we look for the Quick Open Block, we expect a name QO, then containing headers
	// else we continue scanning
	
	private static class FileOrServiceHeaderPart {
		//private boolean isDirectory;
		//private int compressionVersion;
		//private boolean isSolid;
		private int compressionMethod;
		//private long minimumDictionarySize;
		private long unpackedSize;
		private String name;
	}
	
	private static FileOrServiceHeaderPart readFileOrServiceHeaderPart(IO.Readable.Buffered io, byte[] b, ByteBuffer bb) throws IOException {
		FileOrServiceHeaderPart h = new FileOrServiceHeaderPart();
		long fileFlags = readVInt(io);
		//h.isDirectory = (fileFlags & 1) != 0;
		h.unpackedSize = readVInt(io);
		if ((fileFlags & 8) != 0) h.unpackedSize = -1;
		readVInt(io); // file OS-specific attribute
		if ((fileFlags & 2) != 0) {
			readVInt(io); // file modification time
		}
		if ((fileFlags & 4) != 0) {
			io.skip(4); // crc32
		}
		long compressionInfo = readVInt(io);
		//h.compressionVersion = (int)(compressionInfo & 0x3F);
		//h.isSolid = (compressionInfo & 0x40) != 0;
		h.compressionMethod = (int)((compressionInfo>>8) & 0x7);
		//h.minimumDictionarySize = (128*1024)*((compressionInfo>>11)&0xF);
		readVInt(io); // Host OS
		long name_len = readVInt(io);
		bb.clear();
		int l = name_len > 16384 ? 16384 : (int)name_len;
		bb.limit(l);
		int nb = io.readFullySync(bb);
		if (nb != l)
			throw new IOException("RAR file is truncated");
		h.name = new String(b, 0, l, StandardCharsets.UTF_8);
		if (name_len > 16384)
			io.skipSync(name_len - l);
		return h;
	}
	
	private <T extends IO.Readable.Seekable&IO.Readable.Buffered> void readQuickOpen(T io, long pos, long size, long headerOffset, byte[] b, ByteBuffer bb, WorkProgress progress, long work) {
		long p = pos;
		try {
			long w = work;
			do {
				io.seekSync(SeekType.FROM_BEGINNING, p+4);
				readVInt(io); // entry size
				readVInt(io); // flags = 0
				long offset = readVInt(io);
				offset = headerOffset - offset;
				long entryDataSize = readVInt(io);
				p = io.getPosition();
				BlockHeader bh = readBlockHeader(io, p);
				long step = entryDataSize * work / size;
				w -= step;
				p += entryDataSize;
				if (bh.headerType == 2) {
					// file header
					FileOrServiceHeaderPart fh = readFileOrServiceHeaderPart(io, b, bb);
					RARFile file = rar.new RARFile();
					file.compressedSize = bh.dataSize;
					file.uncompressedSize = fh.unpackedSize;
					file.method = fh.compressionMethod;
					file.name = fh.name;
					// TODO
					rar.content.add(file);
				}
				// TODO service headers
				if (progress != null) progress.progress(step);
			} while (p < pos+size);
			if (progress != null) progress.progress(w);
			rar.contentLoaded.unblock();
		} catch (IOException e) {
			RarArchive.logger.error("Error reading RAR 5 Quick Open block", e);
			rar.contentLoaded.error(e);
		}
	}
	
	private static long readVInt(IO.Readable.Buffered io) throws IOException {
		long value = io.read();
		if (value < 0) throw new EOFException();
		if ((value & 0x80) == 0) return value;
		value -= 0x80;
		int shift = 7;
		int i;
		do {
			i = io.read();
			if (i < 0) throw new EOFException();
			value |= ((i & 0x7F) << shift);
			shift += 7;
		} while ((i & 0x80) != 0);
		return value;
	}
	
}
