package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.util.StringUtil;

public class FAT16 extends FAT1216 {

	public FAT16(IO.Readable.Seekable io) {
		super(io);
	}
	
	public FAT16(IO.Readable.Seekable io, byte[] firstSector) throws IOException {
		super(io);
	}

	@Override
	protected void loadFirstSector(byte[] sector) throws IOException {
		fatEntryBits = 16;
		super.loadFirstSector(sector);
	}
	
	@Override
	protected AsyncWork<Long, IOException> getNextCluster(long cluster, byte[] buffer) {
		long pos = reservedSectors * bytesPerSector;
		pos += cluster * 2;
		AsyncWork<Integer, IOException> read = io.readFullyAsync(pos, ByteBuffer.wrap(buffer, 0, 2));
		AsyncWork<Long, IOException> result = new AsyncWork<>();
		read.listenInline((nb) -> {
			if (nb != 2) {
				result.error(new IOException("Unexpected end of FAT file system"));
				return;
			}
			int v = DataUtil.readUnsignedShortLittleEndian(buffer, 0);
			if (v >= 0x002 && v <= 0xFFEF)
				result.unblockSuccess(Long.valueOf(v));
			else if (v >= 0xFFF8)
				result.unblockSuccess(Long.valueOf(-1));
			else {
				if (logger.error()) {
					if (v == 0) logger.error("Invalid FAT16: free cluster after cluster " + cluster);
					else if (v == 0xFFF7) logger.error("Invalid FAT16: bad cluster after cluster " + cluster);
					else logger.error("Invalid FAT16: invalid entry " + StringUtil.encodeHexaPadding(v) + " after cluster " + cluster);
				}
				result.unblockSuccess(Long.valueOf(-1));
			}
		}, result);
		return result;
	}
}
