package net.lecousin.dataformat.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

/**
 * EFI GUID Partition Table
 */
public class GPT {
	
	public GPT() {
	}

	public static class Header {
		public long version;
		public long headerSize;
		public long headerCRC32;
		public long currentLBA;
		public long backupLBA;
		public long firstUsableLBA;
		public long lastUsableLBA;
		public byte[] diskGUID = new byte[16];
		public long partitionsTableLBA;
		public long nbPartitionsEntries;
		public long partitionEntrySize;
		public long partitionsTableCRC32;
	}
	
	public static class PartitionEntry {
		public byte[] typeGUID = new byte[16];
		public byte[] partitionGUID = new byte[16];
		public long firstLBA;
		public long lastLBA;
		public long attributesFlags;
		public String name;
	}
	
	private Header header;
	private PartitionEntry[] partitions;
	
	public Header getHeader() {
		return header;
	}
	
	public PartitionEntry[] getPartitions() {
		return partitions;
	}
	
	public WorkProgress load(IO.Readable.Seekable io, long pos) {
		WorkProgress progress = new WorkProgressImpl(10000);
		loadHeader(io, pos, progress, 10000);
		return progress;
	}
	
	private void loadHeader(IO.Readable.Seekable io, long pos, WorkProgress progress, long work) {
		byte[] buf = new byte[0x80];
		AsyncSupplier<Integer, IOException> read = io.readFullyAsync(pos, ByteBuffer.wrap(buf, 0, 0x5C));
		header = new Header();
		read.thenStart("Read EFI Part header", io.getPriority(), () -> {
			if (!read.isSuccessful()) {
				progress.error(read.getError());
				return;
			}
			if (!ArrayUtil.equals(EFIPartDetector.EFI_HEADER_SIGNATURE, 0, buf, 0, 8) || read.getResult().intValue() != 0x5C) {
				progress.error(new Exception("Invalid EFI Part header"));
				return;
			}
			header.version = DataUtil.Read32U.LE.read(buf, 0x08);
			header.headerSize = DataUtil.Read32U.LE.read(buf, 0x0C);
			header.headerCRC32 = DataUtil.Read32U.LE.read(buf, 0x10);
			header.currentLBA = DataUtil.Read64.LE.read(buf, 0x18);
			header.backupLBA = DataUtil.Read64.LE.read(buf, 0x20);
			header.firstUsableLBA = DataUtil.Read64.LE.read(buf, 0x28);
			header.lastUsableLBA = DataUtil.Read64.LE.read(buf, 0x30);
			System.arraycopy(buf, 0x38, header.diskGUID, 0, 0x10);
			header.partitionsTableLBA = DataUtil.Read64.LE.read(buf, 0x48);
			header.nbPartitionsEntries = DataUtil.Read32U.LE.read(buf, 0x50);
			header.partitionEntrySize = DataUtil.Read32U.LE.read(buf, 0x54);
			header.partitionsTableCRC32 = DataUtil.Read32U.LE.read(buf, 0x58);
			partitions = new PartitionEntry[header.nbPartitionsEntries > 4096 ? 4096 : (int)header.nbPartitionsEntries];
			long step = work / 10;
			progress.progress(step);
			loadPartitionEntry(io, pos + (header.partitionsTableLBA - header.currentLBA) * 512, 0, buf, progress, work - step);
		}, true);
	}
	
	private void loadPartitionEntry(IO.Readable.Seekable io, long pos, int entryIndex, byte[] buf, WorkProgress progress, long work) {
		if (entryIndex == partitions.length) {
			progress.done();
			return;
		}
		AsyncSupplier<Integer, IOException> read = io.readFullyAsync(pos, ByteBuffer.wrap(buf, 0, 0x80));
		read.thenStart("Read EFI Partition entry", io.getPriority(), () -> {
			if (!read.isSuccessful()) {
				progress.error(read.getError());
				return;
			}
			if (read.getResult().intValue() != 0x80) {
				progress.error(new Exception("Invalid EFI Partition entry"));
				return;
			}
			boolean used = false;
			for (int i = 0; i < 16; ++i)
				if (buf[i] != 0) {
					used = true;
					break;
				}
			if (used) {
				partitions[entryIndex] = new PartitionEntry();
				System.arraycopy(buf, 0x00, partitions[entryIndex].typeGUID, 0, 0x10);
				System.arraycopy(buf, 0x10, partitions[entryIndex].partitionGUID, 0, 0x10);
				partitions[entryIndex].firstLBA = DataUtil.Read64.LE.read(buf, 0x20);
				partitions[entryIndex].lastLBA = DataUtil.Read64.LE.read(buf, 0x28);
				partitions[entryIndex].attributesFlags = DataUtil.Read64.LE.read(buf, 0x30);
				int nameSize = 0;
				while (nameSize < 36 && (buf[0x38 + nameSize * 2] != 0 || buf[0x38 + nameSize * 2 + 1] != 0))
					nameSize++;
				partitions[entryIndex].name = new String(buf, 0x38, nameSize * 2, StandardCharsets.UTF_16LE);
			}
			long step = work / (partitions.length - entryIndex);
			progress.progress(step);
			loadPartitionEntry(io, pos + header.partitionEntrySize, entryIndex + 1, buf, progress, work - step);
		}, true);
	}
}
