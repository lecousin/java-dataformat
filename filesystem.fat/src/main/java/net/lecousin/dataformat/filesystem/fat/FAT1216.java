package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public abstract class FAT1216 extends FAT {

	public FAT1216(IO.Readable.Seekable io) {
		super(io);
	}
	
	public FAT1216(IO.Readable.Seekable io, byte[] firstSector) throws IOException {
		super(io);
	}
	
	@Override
	protected void loadFirstSector(byte[] sector) throws IOException {
		super.loadFirstSector(sector);
		maxNbRootEntries = DataUtil.readUnsignedShortLittleEndian(sector, 0x11);
		sectorsPerFat = DataUtil.readUnsignedShortLittleEndian(sector, 0x16);
		serialNumber = DataUtil.readUnsignedIntegerLittleEndian(sector, 0x27);
		volumeLabel = new String(sector, 0x2B, 11).trim();
		dataRegionAddress = (reservedSectors + nbFat * sectorsPerFat) * bytesPerSector;
		dataRegionAddress += maxNbRootEntries * 32;
	}
	
	@Override
	public void listRootEntries(AsyncCollection<FatEntry> listener) {
		long start = (reservedSectors + nbFat * sectorsPerFat) * bytesPerSector;
		listRootEntries(0, start, new FatEntryState(), new byte[sectorsPerCluster * bytesPerSector], listener);
	}
	
	private void listRootEntries(int index, long offset, FatEntryState state, byte[] buffer, AsyncCollection<FatEntry> listener) {
		AsyncWork<Integer, IOException> read = io.readFullyAsync(offset, ByteBuffer.wrap(buffer));
		read.listenAsync(new Task.Cpu.FromRunnable("Read FAT root directory", io.getPriority(), () -> {
			if (read.hasError()) {
				listener.error(read.getError());
				return;
			}
			if (read.getResult().intValue() != buffer.length) {
				listener.error(new IOException("Unexpected end of FAT file system"));
				return;
			}
			List<FatEntry> entries = new ArrayList<>(32);
			for (int i = 0; i < buffer.length / 32; ++i) {
				if (index + i >= maxNbRootEntries) break;
				if (!readDirectoryEntry(buffer, i * 32, state, entries)) {
					if (!entries.isEmpty())
						listener.newElements(entries);
					listener.done();
					return;
				}
			}
			if (index + 32 >= maxNbRootEntries) {
				if (state.entry != null)
					state.end(entries);
			}
			if (!entries.isEmpty())
				listener.newElements(entries);
			if (index + 32 >= maxNbRootEntries)
				listener.done();
			else
				listRootEntries(index + buffer.length / 32, offset + buffer.length, state, buffer, listener);
		}), true);
	}

}
