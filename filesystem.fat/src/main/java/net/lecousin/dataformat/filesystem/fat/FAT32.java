package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.util.StringUtil;

public class FAT32 extends FAT {

	public FAT32(IO.Readable.Seekable io) {
		super(io);
	}
	
	public FAT32(IO.Readable.Seekable io, byte[] firstSector) throws IOException {
		super(io);
	}
	
	protected long rootDirectoryCluster;
	
	@Override
	protected void loadFirstSector(byte[] sector) throws IOException {
		fatEntryBits = 32;
		super.loadFirstSector(sector);
		maxNbRootEntries = 0;
		sectorsPerFat = DataUtil.readUnsignedIntegerLittleEndian(sector, 0x24);
		serialNumber = DataUtil.readUnsignedIntegerLittleEndian(sector, 0x43);
		volumeLabel = new String(sector, 0x47, 11).trim();
		dataRegionAddress = (reservedSectors + nbFat * sectorsPerFat) * bytesPerSector;
		rootDirectoryCluster = DataUtil.readUnsignedIntegerLittleEndian(sector, 0x2C);
	}
	
	@Override
	public void listRootEntries(AsyncCollection<FatEntry> listener) {
		readDirectory(rootDirectoryCluster, listener);
	}

	@Override
	protected AsyncWork<Long, IOException> getNextCluster(long cluster, byte[] buffer) {
		long pos = reservedSectors * bytesPerSector;
		pos += cluster * 4;
		AsyncWork<Integer, IOException> read = io.readFullyAsync(pos, ByteBuffer.wrap(buffer, 0, 4));
		AsyncWork<Long, IOException> result = new AsyncWork<>();
		read.listenInline((nb) -> {
			if (nb != 4) {
				result.error(new IOException("Unexpected end of FAT file system"));
				return;
			}
			long v = DataUtil.readUnsignedIntegerLittleEndian(buffer, 0);
			if (v >= 0x00000002 && v <= 0x0FFFFFEF)
				result.unblockSuccess(Long.valueOf(v));
			else if (v >= 0x0FFFFFF8)
				result.unblockSuccess(Long.valueOf(-1));
			else {
				if (logger.error()) {
					if (v == 0) logger.error("Invalid FAT32: free cluster after cluster " + cluster);
					else if (v == 0xFFF7) logger.error("Invalid FAT32: bad cluster after cluster " + cluster);
					else logger.error("Invalid FAT32: invalid entry " + StringUtil.encodeHexaPadding(v) + " after cluster " + cluster);
				}
				result.unblockSuccess(Long.valueOf(-1));
			}
		}, result);
		return result;
	}
}
