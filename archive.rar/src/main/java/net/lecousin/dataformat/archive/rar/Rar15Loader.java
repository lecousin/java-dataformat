package net.lecousin.dataformat.archive.rar;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import net.lecousin.dataformat.archive.rar.RarArchive.RARFile;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.StringUtil;

class Rar15Loader extends RarLoader {

	Rar15Loader(RarArchive rar, byte[] headerBuf, ByteBuffer headerBuffer, WorkProgress progress, long work) {
		super(rar);
		this.headerBuf = headerBuf;
		this.headerBuffer = headerBuffer;
		buf = new byte[25];
		buffer = ByteBuffer.wrap(buf);
		this.progress = progress;
		this.work = work;
	}
	
	private byte[] headerBuf;
	private ByteBuffer headerBuffer;
	private byte[] buf;
	private ByteBuffer buffer;
	private WorkProgress progress;
	private long work;
	
	@Override
	protected void start() {
		// read archive header block
		headerBuffer.clear();
		AsyncWork<Integer,IOException> readHeader = rar.io.readFullyAsync(7, headerBuffer);
		readHeader.listenInline(new Runnable() {
			@Override
			public void run() {
				if (readHeader.isCancelled()) {
					rar.contentLoaded.cancel(readHeader.getCancelEvent());
					return;
				}
				if (!readHeader.isSuccessful()) {
					RarArchive.getLogger().error("Unable to read RAR header", readHeader.getError());
					rar.contentLoaded.error(readHeader.getError());
					return;
				}
				if (readHeader.getResult().intValue() != 7) {
					rar.contentLoaded.error(new IOException("Invalid RAR Archive: Archive header is truncated"));
					return;
				}
				if (headerBuf[2] != 0x73) {
					RarArchive.getLogger().error("Invalid RAR Archive: archive header (type 0x73) expected after marker header, found type is: "+StringUtil.encodeHexa(headerBuf[2]));
					rar.contentLoaded.error(new IOException("Invalid RAR Archive: archive header (type 0x73) expected after marker header, found type is: "+StringUtil.encodeHexa(headerBuf[2])));
					return;
				}
				rar.isVolumeArchive = (headerBuf[3] & 0x01) != 0;
				// TODO boolean commentPresent = (headerBuf[3] & 0x02) != 0;
				rar.isLocked = (headerBuf[3] & 0x04) != 0;
				rar.isSolid = (headerBuf[3] & 0x08) != 0;
				// TODO boolean newVolumeNamingScheme = (headerBuf[3] & 0x10) != 0;
				// TODO boolean authenticityPresent = (headerBuf[3] & 0x20) != 0;
				// TODO boolean recoveryPresent = (headerBuf[3] & 0x40) != 0;
				// TODO boolean encryptedBlockHeaders = (headerBuf[3] & 0x80) != 0;
				// TODO boolean isFirstVolume = (headerBuf[4] & 0x01) != 0;
				int size = DataUtil.readUnsignedShortLittleEndian(headerBuf, 5);
				if (progress != null) progress.progress(work/10);
				readNextBlock(7+size, progress, work-work/10);
			}
		});
	}
	
	private void readNextBlock(long pos, WorkProgress progress, long work) {
		headerBuffer.clear();
		AsyncWork<Integer,IOException> readHeader = rar.io.readFullyAsync(pos, headerBuffer);
		readHeader.listenInline(new Runnable() {
			@Override
			public void run() {
				if (readHeader.isCancelled()) {
					rar.contentLoaded.cancel(readHeader.getCancelEvent());
					return;
				}
				if (!readHeader.isSuccessful()) {
					RarArchive.getLogger().error("Unable to read RAR block header at "+pos, readHeader.getError());
					rar.contentLoaded.error(readHeader.getError());
					return;
				}
				if (readHeader.getResult().intValue() <= 0) {
					// end of archive
					if (progress != null) progress.progress(work);
					rar.contentLoaded.unblock();
					return;
				}
				if (readHeader.getResult().intValue() != 7) {
					RarArchive.getLogger().error("Invalid RAR Archive: block header is truncated");
					rar.contentLoaded.error(new IOException("Invalid RAR Archive: block header is truncated"));
					return;
				}
				switch (headerBuf[2]) {
				case 0x72:
					RarArchive.getLogger().error("Unexpected marker block inside the file");
					rar.contentLoaded.error(new IOException("Unexpected marker block inside the file"));
					return;
				case 0x73:
					RarArchive.getLogger().error("Unexpected archive header inside the file");
					rar.contentLoaded.error(new IOException("Unexpected archive header inside the file"));
					return;
				case 0x74: // file header
					readFileHeader(pos+7, progress, work);
					break;
				case 0x75: // old style comment header
				case 0x76: // old style authenticity information
				case 0x77: // old style subblock
				case 0x78: // old style recovery record
				case 0x79: // old style authenticity information
				case 0x7A: // subblock
				case 0x7B: // terminator
					goToNextHeader(pos, progress, work);
					break;
				default: // unknown
					RarArchive.getLogger().info("Unknown RAR block type "+StringUtil.encodeHexa(headerBuf[2])+" at "+pos);
					goToNextHeader(pos, progress, work);
					break;
				}
			}
		});
	}
	
	private void goToNextHeader(long pos, WorkProgress progress, long work) {
		int headSize = DataUtil.readUnsignedShortLittleEndian(headerBuf, 5);
		if ((headerBuf[4] & 0x80) != 0) {
			// ADD_SIZE
			headerBuffer.clear();
			headerBuffer.limit(4);
			AsyncWork<Integer,IOException> read2 = rar.io.readFullyAsync(pos+7, headerBuffer);
			read2.listenInline(new Runnable() {
				@Override
				public void run() {
					if (read2.isCancelled()) {
						rar.contentLoaded.cancel(read2.getCancelEvent());
						return;
					}
					if (!read2.isSuccessful()) {
						RarArchive.getLogger().error("Error reading additional header data at "+(pos+7), read2.getError());
						rar.contentLoaded.error(read2.getError());
						return;
					}
					if (read2.getResult().intValue() != 4) {
						RarArchive.getLogger().error("Invalid RAR Archive: block header is truncated");
						rar.contentLoaded.error(new IOException("Invalid RAR Archive: block header is truncated"));
						return;
					}
					long size = headSize + DataUtil.readUnsignedIntegerLittleEndian(headerBuf, 0);
					if (progress != null) progress.progress(work/10);
					readNextBlock(pos + size, progress, work-work/10);
				}
			});
		} else {
			if (progress != null) progress.progress(work/10);
			readNextBlock(pos + headSize, progress, work-work/10);
		}
	}

	
	private void readFileHeader(long pos, WorkProgress progress, long work) {
		buffer.clear();
		AsyncWork<Integer,IOException> read = rar.io.readFullyAsync(pos, buffer);
		read.listenInline(new Runnable() {
			@Override
			public void run() {
				if (read.isCancelled()) {
					rar.contentLoaded.cancel(read.getCancelEvent());
					return;
				}
				if (!read.isSuccessful()) {
					RarArchive.getLogger().error("Error reading file header at "+pos, read.getError());
					rar.contentLoaded.error(read.getError());
					return;
				}
				if (read.getResult().intValue() != 25) {
					RarArchive.getLogger().error("Invalid RAR Archive: file header is truncated");
					rar.contentLoaded.error(new IOException("Invalid RAR Archive: file header is truncated"));
					return;
				}
				RARFile file = rar.new RARFile();
				file.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(buf, 0);
				file.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(buf, 4);
				file.method = buf[18];
				int name_len = DataUtil.readUnsignedShortLittleEndian(buf, 19);
				int add_size = 0;
				if ((headerBuf[4] & 0x01) != 0) {
					add_size += 8; // 64-bit sizes
				}
				if ((headerBuf[4] & 0x04) != 0) {
					add_size += 8; // salt
				}
				// TODO extended time ????? variable length ???
				byte[] b = new byte[name_len+add_size];
				AsyncWork<Integer, IOException> read2 = rar.io.readFullyAsync(pos+25, ByteBuffer.wrap(b));
				read2.listenInline(new Runnable() {
					@Override
					public void run() {
						if (read2.isCancelled()) {
							rar.contentLoaded.cancel(read2.getCancelEvent());
							return;
						}
						if (!read2.isSuccessful()) {
							RarArchive.getLogger().error("Error reading file header at "+pos, read2.getError());
							rar.contentLoaded.error(read2.getError());
							return;
						}
						if (read2.getResult().intValue() != b.length) {
							RarArchive.getLogger().error("Invalid RAR Archive: file header is truncated");
							rar.contentLoaded.error(new IOException("Invalid RAR Archive: file header is truncated"));
							return;
						}
						int i = 0;
						if ((headerBuf[4] & 0x01) != 0) {
							long l = DataUtil.readUnsignedIntegerLittleEndian(b, 0);
							file.compressedSize += (l << 32);
							l = DataUtil.readUnsignedIntegerLittleEndian(b, 4);
							file.uncompressedSize += (l << 32);
							i += 8;
						}
						if ((headerBuf[4] & 0x02) != 0) {
							int z;
							for (z = i; z < i+name_len; ++z)
								if (b[z] == 0) break;
							if (z == i+name_len)
								file.name = new String(b, i, name_len, StandardCharsets.UTF_8);
							else {
								// TODO use unicode name instead ?
								file.name = new String(b, i, z-i);
							}
						} else
							file.name = new String(b, i, name_len);
						i += name_len;
						
						rar.content.add(file);
						
						if (progress != null) progress.progress(work/10);
						readNextBlock(pos+25+b.length+file.compressedSize, progress, work);
					}
				});
			}
		});
	}
	
}
