package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.util.StringUtil;

public class FAT12 extends FAT1216 {
	
	public FAT12(IO.Readable.Seekable io) {
		super(io);
	}
	
	public FAT12(IO.Readable.Seekable io, byte[] firstSector) throws IOException {
		super(io);
	}
	
	@Override
	protected void loadFirstSector(byte[] sector) throws IOException {
		fatEntryBits = 12;
		super.loadFirstSector(sector);
	}
	
	@Override
	protected AsyncWork<Long, IOException> getNextCluster(long cluster, byte[] buffer) {
		long pos = reservedSectors * bytesPerSector;
		long posInFat = 3 * (cluster / 2);
		AsyncWork<Integer, IOException> read = io.readFullyAsync(pos + posInFat, ByteBuffer.wrap(buffer, 0, 3));
		AsyncWork<Long, IOException> result = new AsyncWork<>();
		read.listenInline((nb) -> {
			if (nb != 3) {
				result.error(new IOException("Unexpected end of FAT file system"));
				return;
			}
			int v;
			if ((cluster % 2) == 0) {
				v = (buffer[0] & 0xFF) | ((buffer[1] & 0x0F) << 8);
			} else {
				v = ((buffer[1] & 0xF0) >> 4) | ((buffer[2] & 0xFF) << 4);
			}
			if (v >= 0x002 && v <= 0xFEF)
				result.unblockSuccess(Long.valueOf(v));
			else if (v >= 0xFF8)
				result.unblockSuccess(Long.valueOf(-1));
			else {
				if (logger.error()) {
					if (v == 0) logger.error("Invalid FAT12: free cluster after cluster " + cluster);
					else if (v == 0xFF7) logger.error("Invalid FAT12: bad cluster after cluster " + cluster);
					else logger.error("Invalid FAT12: invalid entry " + StringUtil.encodeHexaPadding(v) + " after cluster " + cluster);
				}
				result.unblockSuccess(Long.valueOf(-1));
			}
		}, result);
		return result;
	}

}
