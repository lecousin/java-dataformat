package net.lecousin.dataformat.archive.zip;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.dataformat.archive.zip.ZipArchiveRecords.EndOfCentralDirectory;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;

public class ZipArchiveScanner {
	
	public static interface EntryListener {
		public Listener<IO.Readable> entryFound(ZippedFile entry, long positionInZip);
		public void centralDirectoryEntryFound(ZippedFile entry);
		public void endOfCentralDirectoryFound(EndOfCentralDirectory end);
	}

	public ZipArchiveScanner(IO.Readable io, EntryListener listener) {
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
	private Logger logger = ZipArchive.getLogger();
	
	public SynchronizationPoint<IOException> scan() {
		SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
		long start = System.currentTimeMillis();
		scan(sp, start);
		return sp;
	}
	
	private void scan(SynchronizationPoint<IOException> sp, long start) {
		searchPKInThread(
			(type) -> {
				// PK found
				ISynchronizationPoint<IOException> read = readPKRecord(type);
				if (read == null)
					return true;
				read.listenInline(() -> { scan(sp, start); }, sp);
				return false;
			},
			() -> {
				// end of file
				if (logger.debug())
					logger.debug("Zip scanned in "+(System.currentTimeMillis()-start)+"ms.");
				sp.unblock();
			},
			0, 0,
			sp
		);
	}
	
	private static interface PKListener {
		boolean pkFound(int type) throws IOException;
	}

	private void searchPKInThread(PKListener onFound, Runnable onEnd, int currentState, int typeFirstByte, ISynchronizationPoint<IOException> sp) {
		new Task.Cpu.FromRunnable("Scanning zip file: " + input.getSourceDescription(), input.getPriority(), () -> {
			searchPKLoop(onFound, onEnd, currentState, typeFirstByte, sp);
		}).start();
	}
	
	private void searchPKLoop(PKListener onFound, Runnable onEnd, int currentState, int typeFirstByte, ISynchronizationPoint<IOException> sp) {
		int state = currentState;
		int type1 = typeFirstByte;
		do {
			int c;
			try { c = input.readAsync(); }
			catch (IOException e) {
				sp.error(e);
				return;
			}
			if (c == -1) {
				onEnd.run();
				return;
			}
			if (c == -2) {
				int s = state;
				int t1 = type1;
				input.canStartReading().listenInline(() -> {
					searchPKInThread(onFound, onEnd, s, t1, sp);
				}, sp);
				return;
			}
			pos++;
			if (state == 0) {
				if (c != 'P') continue;
				state = 1;
				continue;
			}
			if (state == 1) {
				if (c == 'P') continue;
				if (c == 'K') {
					state = 2;
					continue;
				}
				state = 0;
				continue;
			}
			if (state == 2) {
				type1 = c;
				state = 3;
				continue;
			}
			int type = (type1<<8) | c;
			state = 0;
			try {
				if (!onFound.pkFound(type))
					break;
			} catch (IOException e) {
				sp.error(e);
				return;
			}
		} while (true);
	}
	
	private AsyncWork<Boolean, IOException> searchPKType(int searchType) {
		AsyncWork<Boolean, IOException> result = new AsyncWork<>();
		searchPKLoop(
			(type) -> {
				// PK found
				if (type != searchType)
					return true;
				result.unblockSuccess(Boolean.TRUE);
				return false;
			},
			() -> {
				// end of file
				result.unblockSuccess(Boolean.FALSE);
			},
			0, 0,
			result
		);
		return result;
	}

	private ISynchronizationPoint<IOException> readPKRecord(int type) throws IOException {
		switch (type) {
		case ZipArchiveRecords.LocalFileID:
			return readLocalFile();
		case 0x0608: // Archive extra data record
			if (logger.debug())
				logger.debug("Zip scan: extra data record found at "+(pos-4));
			return skipRecordWithSize(4);
		case ZipArchiveRecords.CentralDirectoryID: {
			if (logger.debug())
				logger.debug("Zip scan: central directory found at "+(pos-4));
			// Central directory structure
			AsyncWork<ZippedFile, IOException> read = ZipArchiveRecords.readCentralDirectoryEntry(input);
			if (read.isUnblocked()) {
				if (read.hasError()) throw read.getError();
				if (read.isCancelled()) return null;
				listener.centralDirectoryEntryFound(read.getResult());
				pos += read.getResult().headerLength - 4;
				return null;
			}
			SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
			read.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
				listener.centralDirectoryEntryFound(read.getResult());
				pos += read.getResult().headerLength - 4;
				sp.unblock();
			}), sp);
			return sp; }
		case 0x0505: // Digital signature
			if (logger.debug())
				logger.debug("Zip scan: digital signature found at "+(pos-4));
			return skipRecordWithSize(2);
		case 0x0606:
			if (logger.debug())
				logger.debug("Zip scan: zip 64 end of central directory found at "+(pos-4));
			if (endOfCentralDirectory == null) {
				if (logger.error())
					logger.error("Zip64 end of central directory found, but no end of central directory was found before it");
				return skipRecordWithSize(8);
			}
			// TODO asynchronous
			pos += ZipArchiveRecords.EndOfCentralDirectory.readZip64(input, endOfCentralDirectory);
			return null;
		case 0x0607: // Zip64 end of central directory locator
			if (logger.debug())
				logger.debug("Zip scan: zip 64 end of central directory locator found at "+(pos-4));
			// we should have find the central directory before, just skip it
			pos += 16;
			return skip(16);
		case ZipArchiveRecords.EndOfCentralDirectory.ID:
			if (logger.debug())
				logger.debug("Zip scan: end of central directory found at "+(pos-4));
			// TODO asynchronous
			endOfCentralDirectory = ZipArchiveRecords.EndOfCentralDirectory.read(input);
			listener.endOfCentralDirectoryFound(endOfCentralDirectory);
			pos += endOfCentralDirectory.headerLength;
			return null;
		case 0x0708: // Data descriptor
			if (logger.debug())
				logger.debug("Zip scan: data descriptor found at "+(pos-4));
			pos += 12;
			return skip(12);
		default:
			if (logger.info())
				logger.info("ZipArchive: unknown record 0x"+Integer.toHexString(type)+" in "+input.getSourceDescription()+" at "+(pos-4));
			return null;
		}
	}
	
	private ISynchronizationPoint<IOException> skipRecordWithSize(int size) throws IOException {
		byte[] b = new byte[size];
		AsyncWork<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(b));
		if (r.isUnblocked()) {
			if (r.hasError()) throw r.getError();
			long s;
			switch (size) {
			default:
			case 2: s = DataUtil.readUnsignedShortLittleEndian(b, 0); break;
			case 4: s = DataUtil.readUnsignedIntegerLittleEndian(b, 0); break;
			case 8: s = DataUtil.readLongLittleEndian(b, 0); break;
			}
			pos += size + s;
			return skip(s);
		}
		SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
		r.listenInline(() -> {
			long s;
			switch (size) {
			default:
			case 2: s = DataUtil.readUnsignedShortLittleEndian(b, 0); break;
			case 4: s = DataUtil.readUnsignedIntegerLittleEndian(b, 0); break;
			case 8: s = DataUtil.readLongLittleEndian(b, 0); break;
			}
			pos += size + s;
			AsyncWork<Long, IOException> skip = input.skipAsync(s);
			if (skip.isUnblocked()) {
				if (skip.hasError()) sp.error(skip.getError());
				else sp.unblock();
				return;
			}
			skip.listenInline(sp);
		}, sp);
		return sp;
	}
	
	private ISynchronizationPoint<IOException>skip(long size) throws IOException {
		AsyncWork<Long, IOException> skip = input.skipAsync(size);
		if (skip.isUnblocked()) {
			if (skip.hasError()) throw skip.getError();
			return null;
		}
		return skip;
	}
	
	private ISynchronizationPoint<IOException> readLocalFile() throws IOException {
		if (logger.debug())
			logger.debug("Zip scan: local entry found at "+(pos-4));
		// Local File Header
		AsyncWork<ZippedFile, IOException> readEntry = ZipArchiveRecords.readLocalFileEntry(input, true, pos - 4);
		if (!readEntry.isUnblocked()) {
			SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
			readEntry.listenAsync(new Task.Cpu.FromRunnable("Read ZIP local file entry", input.getPriority(), () -> {
				try { readLocalFile(readEntry.getResult(), sp); }
				catch (IOException e) { sp.error(e); }
			}) , sp);
			return sp;
		}
		if (readEntry.hasError())
			throw readEntry.getError();
		return readLocalFile(readEntry.getResult(), null);
	}
	
	private ISynchronizationPoint<IOException> readLocalFile(ZippedFile entry, SynchronizationPoint<IOException> sp) throws IOException {
		Listener<IO.Readable> uncompress = listener.entryFound(entry, pos);
		pos += entry.headerLength - 4;
		if (entry.isEcrypted()) {
			// we should have a 12-bytes encryption header before the data
			AsyncWork<Long, IOException> skip = input.skipAsync(12);
			pos += 12;
			if (!skip.isUnblocked()) {
				SynchronizationPoint<IOException> sp2 = sp == null ? new SynchronizationPoint<>() : sp;
				skip.listenAsync(new Task.Cpu.FromRunnable("Read ZIP local file entry", input.getPriority(), () -> {
					try { readLocalFile(entry, sp2, uncompress); }
					catch (IOException e) { sp2.error(e); }
				}), sp2);
				return sp;
			}
			if (skip.hasError())
				throw skip.getError();
		}
		return readLocalFile(entry, sp, uncompress);
	}
	
	private ISynchronizationPoint<IOException> readLocalFile(ZippedFile entry, SynchronizationPoint<IOException> sp, Listener<IO.Readable> uncompress) throws IOException {
		if (uncompress == null) {
			// skip
			if ((entry.flags & 8) == 0) {
				AsyncWork<Long, IOException> skip = input.skipAsync(entry.compressedSize);
				pos += entry.compressedSize;
				if (!skip.isUnblocked()) {
					if (sp == null)
						return skip;
					skip.listenInline(sp);
					return sp;
				}
				if (sp != null)
					sp.unblock();
				return sp;
			}
			// search following data descriptor
			AsyncWork<Boolean, IOException> search = searchPKType(0x0708);
			if (search.isUnblocked()) {
				if (search.hasError())
					throw search.getError();
				return readLocalFileDataDescriptor(entry, search, sp);
			}
			SynchronizationPoint<IOException> sp2 = sp != null ? sp : new SynchronizationPoint<>();
			search.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
				try { readLocalFileDataDescriptor(entry, search, sp2); }
				catch (IOException e) { sp2.error(e); }
			}), sp2);
			return sp2;
		}
		
		if ((entry.flags & 8) == 0) {
			uncompress.fire(entry.uncompressInline(input));
			return sp;
		}
		// search following data descriptor
		AsyncWork<Boolean, IOException> search = searchPKType(0x0708);
		if (search.isUnblocked()) {
			if (search.hasError())
				throw search.getError();
			ISynchronizationPoint<IOException> spRead = readLocalFileDataDescriptor(entry, search, null);
			if (spRead == null) {
				callUncompress(uncompress, entry);
				sp.unblock();
				return sp;
			}
			SynchronizationPoint<IOException> sp2 = sp != null ? sp : new SynchronizationPoint<>();
			spRead.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
				try {
					callUncompress(uncompress, entry);
					sp2.unblock();
				} catch (IOException e) {
					sp2.error(e);
				}
			}), sp2);
			return sp2;
		}
		SynchronizationPoint<IOException> sp2 = sp != null ? sp : new SynchronizationPoint<>();
		search.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
			ISynchronizationPoint<IOException> spRead;
			try { spRead = readLocalFileDataDescriptor(entry, search, null); }
			catch (IOException e) {
				sp2.error(e);
				return;
			}
			if (spRead == null) {
				try {
					callUncompress(uncompress, entry);
					sp2.unblock();
				} catch (IOException e) {
					sp2.error(e);
				}
				return;
			}
			spRead.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
				try {
					callUncompress(uncompress, entry);
					sp2.unblock();
				} catch (IOException e) {
					sp2.error(e);
				}
			}), sp2);
		}), sp2);
		return sp2;
	}
	
	private void callUncompress(Listener<IO.Readable> uncompress, ZippedFile entry) throws IOException {
		if (input instanceof IO.Readable.Seekable) {
			((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, entry.offset+entry.headerLength);
			uncompress.fire(entry.uncompressInline(input));
			((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, pos);
		} else
			uncompress.fire(null);
	}
	
	private ISynchronizationPoint<IOException> readLocalFileDataDescriptor(ZippedFile entry, AsyncWork<Boolean, IOException> search, SynchronizationPoint<IOException> sp) throws IOException {
		if (!search.getResult().booleanValue()) {
			if (logger.error())
				logger.error("Zip scan: Local entry found, but not the data descriptor");
			if (sp != null)
				sp.unblock();
			return sp;
		}
		if (logger.debug())
			logger.debug("Zip scan: data descriptor found after local entry at "+(pos-4));
		byte[] buf = new byte[12];
		AsyncWork<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(buf));
		if (r.isUnblocked()) {
			if (r.hasError())
				throw r.getError();
			readLocalFileDataDescriptor(entry, buf);
			if (sp != null)
				sp.unblock();
			return sp;
		}
		SynchronizationPoint<IOException> sp2 = sp != null ? sp : new SynchronizationPoint<>();
		r.listenAsync(new Task.Cpu.FromRunnable("Scan ZIP", input.getPriority(), () -> {
			readLocalFileDataDescriptor(entry, buf);
			sp2.unblock();
		}), sp2);
		return sp2;
	}

	private void readLocalFileDataDescriptor(ZippedFile entry, byte[] buf) {
		entry.crc32 = DataUtil.readUnsignedIntegerLittleEndian(buf, 0);
		entry.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(buf, 4);
		entry.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(buf, 8);
		pos += 12;
	}
	
}
