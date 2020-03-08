package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;

public abstract class FAT {
	
	public static AsyncSupplier<FAT, IOException> open(IO.Readable.Seekable io) {
		byte[] sector = new byte[512];
		AsyncSupplier<Integer, IOException> read = io.readFullyAsync(0, ByteBuffer.wrap(sector));
		AsyncSupplier<FAT, IOException> result = new AsyncSupplier<>();
		read.thenStart("Opening FAT File system", io.getPriority(), () -> {
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
		}, result);
		return result;
	}
	
	protected FAT(IO.Readable.Seekable io) {
		this.io = io;
		byte[] sector = new byte[512];
		AsyncSupplier<Integer, IOException> read = io.readFullyAsync(0, ByteBuffer.wrap(sector));
		read.thenStart("Opening FAT File system", io.getPriority(), () -> {
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
		}, loaded);
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
	protected Async<IOException> loaded = new Async<>();
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
		bytesPerSector = DataUtil.Read16U.LE.read(sector, 0x0B);
		sectorsPerCluster = (short)(sector[0x0D] & 0xFF);
		reservedSectors = DataUtil.Read16U.LE.read(sector, 0x0E);
		nbFat = (short)(sector[0x10] & 0xFF);
		totalSectors = DataUtil.Read16U.LE.read(sector, 0x13);
		if (totalSectors == 0) totalSectors = DataUtil.Read32U.LE.read(sector, 0x20);
	}
	
	public IAsync<IOException> getLoadedSynch() {
		return loaded;
	}
	
	public void close() throws Exception {
		io.close();
	}
	
	public abstract void listRootEntries(AsyncCollection<FatEntry> listener);
	
	protected static class FatEntryState {
		protected FatEntry entry = null;
		protected byte[] lfn = null;
		protected int lfnPos = 0;
		protected int lastLFNSequence = -1;
		protected byte lfnChecksum = 0;
		
		protected void end(List<FatEntry> entries) {
			if (lastLFNSequence == 1) {
				int nb = 0;
				while (nb < lfn.length - lfnPos && (lfn[lfnPos + nb] != 0 || lfn[lfnPos + nb + 1] != 0))
					nb += 2;
				entry.longName = new String(lfn, lfnPos, nb, StandardCharsets.UTF_16LE);
			}
			entries.add(entry);
			reset();
		}
		
		protected void reset() {
			lfn = null;
			lastLFNSequence = -1;
			lfnPos = -1;
			entry = null;			
		}
	}
	
	protected boolean readDirectoryEntry(byte[] sector, int offset, FatEntryState currentState, List<FatEntry> entries) {
		if (sector[offset] == 0)
			return false; // end of directory
		if ((sector[offset] & 0xFF) == 0xE5) {
			currentState.reset();
			return true;
		}
		byte attr = sector[offset + 0x0B];
		if (attr == 0x0F) {
			// LFN
			/*
			byte sum = 0;
			int dot = currentState.entry.shortName.indexOf('.');
			if (dot == -1) dot = currentState.entry.shortName.length();
			for (int i = 0; i < 8; ++i) {
				byte val = dot > i ? (byte)currentState.entry.shortName.charAt(i) : 0x20;
				sum = (byte)((byte)(((sum & 1) << 7) + ((sum & 0xFE) >> 1)) + (byte)val);
			}
			for (int i = 0; i < 3; ++i) {
				byte val = currentState.entry.shortName.length() > dot + 1 + i ? (byte)currentState.entry.shortName.charAt(dot + 1 + i) : 0x20;
				sum = (byte)((byte)(((sum & 1) << 7) + ((sum & 0xFE) >> 1)) + (byte)val);
			}
			int sum2 = 0;
			for (int i = 0; i < 8; ++i) {
				byte val = dot > i ? (byte)currentState.entry.shortName.charAt(i) : 0x20;
				sum2 = val + (((sum2 & 1) << 7) + ((sum2 & 0xfe) >> 1));
			}
			for (int i = 0; i < 3; ++i) {
				byte val = currentState.entry.shortName.length() > dot + 1 + i ? (byte)currentState.entry.shortName.charAt(dot + 1 + i) : 0x20;
				sum2 = val + (((sum2 & 1) << 7) + ((sum2 & 0xfe) >> 1));
			}
			if ((sector[offset + 0x0D]) != (byte)sum) {
				currentState.lfn = null;
				currentState.end(entries);
				return true;
			}
			*/
			if ((sector[offset] & 0x40) != 0) {
				// first LFN entry
				currentState.lastLFNSequence = sector[offset] & 0x1F;
				currentState.lfn = new byte[20 * 26];
				currentState.lfnPos = currentState.lfn.length;
				currentState.lfnChecksum = sector[offset + 0x0D];
			} else if ((sector[offset] & 0x1F) != currentState.lastLFNSequence - 1 || sector[offset + 0x0D] != currentState.lfnChecksum) {
				// not expected
				currentState.reset();
				return true;
			} else
				currentState.lastLFNSequence--;
			currentState.lfnPos -= 4;
			System.arraycopy(sector, offset + 0x1C, currentState.lfn, currentState.lfnPos, 4);
			currentState.lfnPos -= 12;
			System.arraycopy(sector, offset + 0x0E, currentState.lfn, currentState.lfnPos, 12);
			currentState.lfnPos -= 10;
			System.arraycopy(sector, offset + 1, currentState.lfn, currentState.lfnPos, 10);
			return true;
		}
		// DOS entry
		if (sector[offset] == 0x2E) {
			// dot
			if ((attr & 0x10) != 0) {
				// directory
				if (sector[offset + 1] == ' ') {
					currentState.reset();
					return true;
				}
				if (sector[offset + 1] == '.' && sector[offset + 2] == ' ') {
					currentState.reset();
					return true;
				}
			}
		}
		currentState.entry = new FatEntry();
		if (sector[offset] == 5) sector[offset] = (byte)0xE5;
		currentState.entry.shortName = new String(sector, offset, 8, StandardCharsets.US_ASCII).trim();
		String ext = new String(sector, offset + 8, 3, StandardCharsets.US_ASCII).trim();
		if (ext.length() > 0) currentState.entry.shortName += "." + ext;
		currentState.entry.attributes = attr;
		currentState.entry.cluster = DataUtil.Read16U.LE.read(sector, offset + 0x1A);
		if (fatEntryBits == 32)
			currentState.entry.cluster |= ((long)DataUtil.Read16U.LE.read(sector, offset + 0x14)) << 16;
		currentState.entry.size = DataUtil.Read32U.LE.read(sector, offset + 0x1C);
		// TODO dates...
		
		if (currentState.lastLFNSequence == 1) {
			int sum = 0;
			for (int i = 0; i < 11; ++i)
				sum = sector[offset + i] + (((sum & 1) << 7) + ((sum & 0xfe) >> 1));
			if (currentState.lfnChecksum != (byte)(sum & 0xFF))
				currentState.lastLFNSequence = -1;
		}
		currentState.end(entries);
		return true;
	}
	
	protected void readDirectory(long firstCluster, AsyncCollection<FatEntry> listener) {
		readDirectory(firstCluster, new FatEntryState(), new byte[sectorsPerCluster * bytesPerSector], listener);
	}

	private void readDirectory(long cluster, FatEntryState state, byte[] buffer, AsyncCollection<FatEntry> listener) {
		long offset = dataRegionAddress + (cluster - 2) * sectorsPerCluster * bytesPerSector;
		AsyncSupplier<Integer, IOException> read = io.readFullyAsync(offset, ByteBuffer.wrap(buffer));
		read.thenStart("Read FAT directory", io.getPriority(), () -> {
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
				if (!readDirectoryEntry(buffer, i * 32, state, entries)) {
					if (!entries.isEmpty())
						listener.newElements(entries);
					listener.done();
					return;
				}
			}
			if (!entries.isEmpty())
				listener.newElements(entries);
			getNextCluster(cluster, buffer).onDone(
				(res) -> {
					if (res == -1)
						listener.done();
					else
						readDirectory(res, state, buffer, listener);
				},
				(error) -> { listener.error(error); },
				(cancel) -> { listener.error(cancel); }
			);
		}, true);
	}
	
	protected abstract AsyncSupplier<Long, IOException> getNextCluster(long cluster, byte[] buffer);
}
