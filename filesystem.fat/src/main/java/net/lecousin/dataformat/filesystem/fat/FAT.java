package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;

public abstract class FAT {
	
	public static AsyncWork<FAT, IOException> open(IO.Readable.Seekable io) {
		byte[] sector = new byte[512];
		AsyncWork<Integer, IOException> read = io.readFullyAsync(0, ByteBuffer.wrap(sector));
		AsyncWork<FAT, IOException> result = new AsyncWork<>();
		read.listenAsync(new Task.Cpu.FromRunnable("Opening FAT File system", io.getPriority(), () -> {
			if (read.getResult().intValue() == 512) {
				if (sector[0x36] == 'F' && sector[0x37] == 'A' && sector[0x38] == 'T' && sector[0x39] == '1') {
					if (sector[0x3A] == '2') {
						try {
							FAT12 fat = new FAT12(io, sector);
							result.unblockSuccess(fat);
						} catch (IOException e) {
							result.error(e);
						}
						return;
					}
					if (sector[0x3A] == '6') {
						try {
							FAT16 fat = new FAT16(io, sector);
							result.unblockSuccess(fat);
						} catch (IOException e) {
							result.error(e);
						}
						return;
					}
				}
				if (sector[0x52] == 'F' && sector[0x53] == 'A' && sector[0x54] == 'T' && sector[0x55] == '3' && sector[0x56] == '2') {
					try {
						FAT32 fat = new FAT32(io, sector);
						result.unblockSuccess(fat);
					} catch (IOException e) {
						result.error(e);
					}
					return;
				}
			}
			result.error(new IOException("Not a FAT file system"));
		}), result);
		return result;
	}
	
	protected FAT(IO.Readable.Seekable io) {
		this.io = io;
		byte[] sector = new byte[512];
		AsyncWork<Integer, IOException> read = io.readFullyAsync(0, ByteBuffer.wrap(sector));
		read.listenAsync(new Task.Cpu.FromRunnable("Opening FAT File system", io.getPriority(), () -> {
			if (read.getResult().intValue() == 512) {
				if (sector[0x36] == 'F' && sector[0x37] == 'A' && sector[0x38] == 'T' && sector[0x39] == '1') {
					if (sector[0x3A] == '2') {
						if (!(FAT.this instanceof FAT12)) {
							loaded.error(new IOException("This is a FAT12 file system"));
							return;
						}
						try {
							loadFirstSector(sector);
							loaded.unblock();
						} catch (IOException e) {
							loaded.error(e);
						}
						return;
					}
					if (sector[0x3A] == '6') {
						if (!(FAT.this instanceof FAT16)) {
							loaded.error(new IOException("This is a FAT16 file system"));
							return;
						}
						try {
							loadFirstSector(sector);
							loaded.unblock();
						} catch (IOException e) {
							loaded.error(e);
						}
						return;
					}
				}
				if (sector[0x52] == 'F' && sector[0x53] == 'A' && sector[0x54] == 'T' && sector[0x55] == '3' && sector[0x56] == '2') {
					if (!(FAT.this instanceof FAT32)) {
						loaded.error(new IOException("This is a FAT32 file system"));
						return;
					}
					try {
						loadFirstSector(sector);
						loaded.unblock();
					} catch (IOException e) {
						loaded.error(e);
					}
					return;
				}
			}
			loaded.error(new IOException("Not a FAT file system"));
		}), loaded);
	}
	
	protected FAT(IO.Readable.Seekable io, byte[] firstSector) throws IOException {
		this.io = io;
		try {
			loadFirstSector(firstSector);
			loaded.unblock();
		} catch (IOException e) {
			loaded.error(e);
			throw e;
		}
	}
	
	protected IO.Readable.Seekable io;
	protected SynchronizationPoint<IOException> loaded = new SynchronizationPoint<>();
	protected Logger logger = LCCore.getApplication().getLoggerFactory().getLogger(FAT.class);
	
	protected byte fatEntryBits;
	protected String formatterSystem;
	protected int bytesPerSector;
	protected short sectorsPerCluster;
	protected int reservedSectors;
	protected short nbFat;
	protected long totalSectors;
	protected int maxNbRootEntries;
	protected long sectorsPerFat;
	protected long dataRegionAddress;
	protected long serialNumber;
	protected String volumeLabel;
	
	protected void loadFirstSector(byte[] sector) throws IOException {
		formatterSystem = new String(sector, 0x03, 8).trim();
		bytesPerSector = DataUtil.readUnsignedShortLittleEndian(sector, 0x0B);
		sectorsPerCluster = (short)(sector[0x0D] & 0xFF);
		reservedSectors = DataUtil.readUnsignedShortLittleEndian(sector, 0x0E);
		nbFat = (short)(sector[0x10] & 0xFF);
		totalSectors = DataUtil.readUnsignedShortLittleEndian(sector, 0x13);
		if (totalSectors == 0) totalSectors = DataUtil.readUnsignedIntegerLittleEndian(sector, 0x20);
	}
	
	public ISynchronizationPoint<IOException> getLoadedSynch() {
		return loaded;
	}
	
	public void close() throws Exception {
		io.close();
	}
	
	public abstract void listRootEntries(AsyncCollection<FatEntry> listener);
	
	protected static class FatEntryState {
		protected FatEntry entry = null;
		protected byte[] lfn = null;
		protected int lfnUsed = 0;
		protected int lastLFNSequence = -1;
		
		protected void end(List<FatEntry> entries) {
			if (lastLFNSequence == 1) {
				int nb = 0;
				while (nb < lfn.length && lfn[nb] != 0)
					nb++;
				entry.longName = new String(lfn, 0, nb, StandardCharsets.UTF_16);
			}
			lfn = null;
			lastLFNSequence = -1;
			lfnUsed = 0;
			entries.add(entry);
			entry = null;
		}
	}
	
	protected boolean readDirectoryEntry(byte[] sector, int offset, FatEntryState currentState, List<FatEntry> entries) {
		if (sector[offset] == 0) {
			if (currentState.entry != null)
				currentState.end(entries);
			return false; // end of directory
		}
		if ((sector[offset] & 0xFF) == 0xE5) {
			if (currentState.entry == null)
				return true;
			currentState.end(entries);
			return true;
		}
		byte attr = sector[offset + 0x0B];
		if (attr == 0x0F) {
			// LFN
			if (currentState.entry == null)
				return true;
			byte sum = 0;
			int dot = currentState.entry.shortName.indexOf('.');
			for (int i = 0; i < 8; ++i) {
				int val = dot > i ? currentState.entry.shortName.charAt(i) : 0x20;
				sum = (byte)((byte)(((sum & 1) << 7) + ((sum & 0xFE) >> 1)) + (byte)val);
			}
			for (int i = 0; i < 3; ++i) {
				int val = currentState.entry.shortName.length() > dot + 1 + i ? currentState.entry.shortName.charAt(dot + 1 + i) : 0x20;
				sum = (byte)((byte)(((sum & 1) << 7) + ((sum & 0xFE) >> 1)) + (byte)val);
			}
			if ((sector[offset + 0x0D]) != sum) {
				currentState.lfn = null;
				currentState.end(entries);
				return true;
			}
			if ((sector[offset] & 0x40) != 0) {
				// first LFN entry
				if (currentState.lastLFNSequence != -1) {
					// not expected
					currentState.lfn = null;
					currentState.end(entries);
					return true;
				}
				currentState.lastLFNSequence = sector[offset] & 0x1F;
				currentState.lfn = new byte[20 * 26];
			} else if ((sector[offset] & 0x1F) != currentState.lastLFNSequence - 1) {
				// not expected
				currentState.lfn = null;
				currentState.end(entries);
				return true;
			} else
				currentState.lastLFNSequence--;
			System.arraycopy(sector, offset + 1, currentState.lfn, currentState.lfnUsed, 10);
			currentState.lfnUsed += 10;
			System.arraycopy(sector, offset + 0x0E, currentState.lfn, currentState.lfnUsed, 12);
			currentState.lfnUsed += 12;
			System.arraycopy(sector, offset + 0x1C, currentState.lfn, currentState.lfnUsed, 4);
			currentState.lfnUsed += 4;
			if (currentState.lastLFNSequence == 1)
				currentState.end(entries);
			return true;
		}
		// DOS entry
		if (currentState.entry != null)
			currentState.end(entries);
		if (sector[offset] == 0x2E) {
			// dot
			if ((attr & 0x10) != 0) {
				// directory
				if (sector[offset + 1] == ' ')
					return true;
				if (sector[offset + 1] == '.' && sector[offset + 2] == ' ')
					return true;
			}
		}
		currentState.entry = new FatEntry();
		if (sector[offset] == 5) sector[offset] = (byte)0xE5;
		currentState.entry.shortName = new String(sector, offset, 8, StandardCharsets.US_ASCII).trim() + '.' + new String(sector, offset + 8, 3, StandardCharsets.US_ASCII).trim();
		currentState.entry.attributes = attr;
		currentState.entry.cluster = DataUtil.readUnsignedShortLittleEndian(sector, offset + 0x1A);
		if (fatEntryBits == 32)
			currentState.entry.cluster |= ((long)DataUtil.readUnsignedShortLittleEndian(sector, offset + 0x14)) << 16;
		currentState.entry.size = DataUtil.readUnsignedIntegerLittleEndian(sector, offset + 0x1C);
		// TODO dates...
		return true;
	}
	
	protected void readDirectory(long firstCluster, AsyncCollection<FatEntry> listener) {
		readDirectory(firstCluster, new FatEntryState(), new byte[512], listener);
	}

	private void readDirectory(long cluster, FatEntryState state, byte[] buffer, AsyncCollection<FatEntry> listener) {
		long offset = dataRegionAddress + (cluster - 2) * sectorsPerCluster * bytesPerSector;
		AsyncWork<Integer, IOException> read = io.readFullyAsync(offset, ByteBuffer.wrap(buffer));
		read.listenAsync(new Task.Cpu.FromRunnable("Read FAT directory", io.getPriority(), () -> {
			if (read.hasError()) {
				listener.error(read.getError());
				return;
			}
			if (read.getResult().intValue() != 512) {
				listener.error(new IOException("Unexpected end of FAT file system"));
				return;
			}
			List<FatEntry> entries = new ArrayList<>(32);
			for (int i = 0; i < 32; ++i) {
				if (!readDirectoryEntry(buffer, i * 32, state, entries)) {
					if (!entries.isEmpty())
						listener.newElements(entries);
					listener.done();
					return;
				}
			}
			if (!entries.isEmpty())
				listener.newElements(entries);
			getNextCluster(cluster, buffer).listenInline(
				(res) -> {
					if (res == -1) {
						if (state.entry != null) {
							entries.clear();
							state.end(entries);
							listener.newElements(entries);
						}
						listener.done();
					} else
						readDirectory(res, state, buffer, listener);
				},
				(error) -> { listener.error(error); },
				(cancel) -> { listener.error(cancel); }
			);
		}), true);
	}
	
	protected abstract AsyncWork<Long, IOException> getNextCluster(long cluster, byte[] buffer);
}
