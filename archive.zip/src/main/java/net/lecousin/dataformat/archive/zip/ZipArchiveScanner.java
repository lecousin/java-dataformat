package net.lecousin.dataformat.archive.zip;

import java.io.IOException;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.dataformat.archive.zip.ZipArchiveRecords.EndOfCentralDirectory;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.mutable.MutableLong;

public class ZipArchiveScanner extends Task.Cpu<Void,IOException> {
	
	public static interface EntryListener {
		public Listener<IO.Readable> entryFound(ZippedFile entry, long positionInZip);
		public void centralDirectoryEntryFound(ZippedFile entry);
		public void endOfCentralDirectoryFound(EndOfCentralDirectory end);
	}

	public ZipArchiveScanner(IO.Readable io, EntryListener listener) {
		super("Scanning zip file: "+io.getSourceDescription(), io.getPriority());
		this.listener = listener;
		if (io instanceof IO.Readable.Buffered)
			input = (IO.Readable.Buffered)io;
		else
			input = new PreBufferedReadable(io, 4096, io.getPriority(), 65536, io.getPriority(), 32);
	}
	
	private IO.Readable.Buffered input;
	private EntryListener listener;
	private long pos = 0;
	private EndOfCentralDirectory endOfCentralDirectory = null;

	@Override
	public Void run() throws IOException {
		long start = System.currentTimeMillis();
		do {
			// search for PK header
			if (!searchPK())
				break;
			int type = readPKRecordType();
			if (type < 0) break;
			readPKRecord(type);
		} while (true);
		if (ZipArchive.logger.isDebugEnabled())
			ZipArchive.logger.debug("Zip scanned in "+(System.currentTimeMillis()-start)+"ms.");
		return null;
	}
	private boolean searchPK() throws IOException {
		do {
			int c = input.read();
			if (c < 0) return false;
			pos++;
			if (c != 'P') continue;
			do {
				c = input.read();
				if (c < 0) return false;
				pos++;
				if (c == 'P')
					continue;
				if (c == 'K')
					return true;
				break;
			} while (true);
		} while (true);		
	}
	private int readPKRecordType() throws IOException {
		int type1 = input.read();
		if (type1 < 0) return -1;
		pos++;
		int type2 = input.read();
		if (type2 < 0) return -1;
		pos++;
		return (type1<<8) | type2;
	}
	private boolean searchPKType(int type) throws IOException {
		do {
			if (!searchPK())
				return false;
			int t = readPKRecordType();
			if (t < 0) return false;
			if (t == type) return true;
		} while (true);
	}
	private void readPKRecord(int type) throws IOException {
		do {
			int nextType = -2;
			switch (type) {
			case ZipArchiveRecords.LocalFileID: {
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: local entry found at "+(pos-4));
				// Local File Header
				ZippedFile entry = ZipArchiveRecords.readLocalFileEntry(input, true, pos - 4);
				Listener<IO.Readable> uncompress = listener.entryFound(entry, pos);
				pos += entry.headerLength - 4;
				if (entry.isEcrypted()) {
					// we should have a 12-bytes encryption header before the data
					input.skip(12);
					pos += 12;
				}
				if (uncompress == null) {
					// skip
					if ((entry.flags & 8) == 0) {
						input.skipSync(entry.compressedSize);
						pos += entry.compressedSize;
					} else {
						// search following data descriptor
						if (!searchPKType(0x0708)) {
							if (ZipArchive.logger.isErrorEnabled())
								ZipArchive.logger.error("Zip scan: Local entry found, but not the data descriptor");
						} else {
							if (ZipArchive.logger.isDebugEnabled())
								ZipArchive.logger.debug("Zip scan: data descriptor found after local entry at "+(pos-4));
							entry.crc32 = DataUtil.readUnsignedIntegerLittleEndian(input);
							entry.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
							entry.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
							pos += 12;
						}
					}
				} else {
					if ((entry.flags & 8) == 0) {
						uncompress.fire(entry.uncompressInline(input));
					} else {
						// search following data descriptor
						if (!searchPKType(0x0708)) {
							if (ZipArchive.logger.isErrorEnabled())
								ZipArchive.logger.error("Zip scan: Local entry found, but not the data descriptor");
						} else {
							if (ZipArchive.logger.isDebugEnabled())
								ZipArchive.logger.debug("Zip scan: data descriptor found after local entry at "+(pos-4));
							entry.crc32 = DataUtil.readUnsignedIntegerLittleEndian(input);
							entry.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
							entry.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
							pos += 12;
							if (input instanceof IO.Readable.Seekable) {
								((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, entry.offset+entry.headerLength);
								uncompress.fire(entry.uncompressInline(input));
								((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, pos);
							} else
								uncompress.fire(null);
						}
					}
				}
				break; }
			case 0x0608: { // Archive extra data record
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: extra data record found at "+(pos-4));
				long size = DataUtil.readUnsignedIntegerLittleEndian(input);
				input.skipSync(size);
				pos += size + 4;
				break; }
			case ZipArchiveRecords.CentralDirectoryID: {
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: central directory found at "+(pos-4));
				// Central directory structure
				MutableLong p = new MutableLong(pos);
				nextType = ZipArchiveRecords.readCentralDirectory(input, true, p, new Listener<ZippedFile>() {
					@Override
					public void fire(ZippedFile event) {
						listener.centralDirectoryEntryFound(event);
					}
				});
				pos = p.get();
				break; }
			case 0x0505: { // Digital signature
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: digital signature found at "+(pos-4));
				int size = DataUtil.readUnsignedShortLittleEndian(input);
				input.skip(size);
				pos += size + 4;
				break; }
			case 0x0606:
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: zip 64 end of central directory found at "+(pos-4));
				if (endOfCentralDirectory == null) {
					if (ZipArchive.logger.isErrorEnabled())
						ZipArchive.logger.error("Zip64 end of central directory found, but no end of central directory was found before it");
					long size = DataUtil.readLongLittleEndian(input);
					input.skipSync(size);
					pos += size + 8;
				} else
					pos += ZipArchiveRecords.EndOfCentralDirectory.readZip64(input, endOfCentralDirectory);
				break;
			case 0x0607: // Zip64 end of central directory locator
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: zip 64 end of central directory locator found at "+(pos-4));
				// we should have find the central directory before, just skip it
				input.skip(16);
				pos += 16;
				break;
			case ZipArchiveRecords.EndOfCentralDirectory.ID:
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: end of central directory found at "+(pos-4));
				endOfCentralDirectory = ZipArchiveRecords.EndOfCentralDirectory.read(input);
				listener.endOfCentralDirectoryFound(endOfCentralDirectory);
				pos += endOfCentralDirectory.headerLength;
				break;
			case 0x0708: // Data descriptor
				if (ZipArchive.logger.isDebugEnabled())
					ZipArchive.logger.debug("Zip scan: data descriptor found at "+(pos-4));
				input.skip(12);
				pos += 12;
				break;
			default:
				if (ZipArchive.logger.isInfoEnabled())
					ZipArchive.logger.info("ZipArchive: unknown record 0x"+Integer.toHexString(type)+" in "+input.getSourceDescription()+" at "+(pos-4));
				break;
			}
			if (nextType >= 0)
				type = nextType;
			else break;
		} while (true);
	}
}
