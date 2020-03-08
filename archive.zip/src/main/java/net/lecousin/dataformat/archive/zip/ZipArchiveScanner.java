package net.lecousin.dataformat.archive.zip;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.dataformat.archive.zip.ZipArchiveRecords.EndOfCentralDirectory;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;

public class ZipArchiveScanner {
	
	public static interface EntryListener {
		public Consumer<IO.Readable> entryFound(ZippedFile entry, long positionInZip);
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
	
	public Async<IOException> scan() {
		Async<IOException> sp = new Async<>();
		long start = System.currentTimeMillis();
		scan(sp, start);
		return sp;
	}
	
	private void scan(Async<IOException> sp, long start) {
		searchPKInThread(
			(type) -> {
				// PK found
				IAsync<IOException> read = readPKRecord(type);
				if (read == null)
					return true;
				read.onDone(() -> { scan(sp, start); }, sp);
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

	private void searchPKInThread(PKListener onFound, Runnable onEnd, int currentState, int typeFirstByte, IAsync<IOException> sp) {
		Task.cpu("Scanning zip file: " + input.getSourceDescription(), input.getPriority(), t -> {
			searchPKLoop(onFound, onEnd, currentState, typeFirstByte, sp);
			return null;
		}).start();
	}
	
	private void searchPKLoop(PKListener onFound, Runnable onEnd, int currentState, int typeFirstByte, IAsync<IOException> sp) {
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
				input.canStartReading().onDone(() -> {
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
	
	private AsyncSupplier<Boolean, IOException> searchPKType(int searchType) {
		AsyncSupplier<Boolean, IOException> result = new AsyncSupplier<>();
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

	private IAsync<IOException> readPKRecord(int type) throws IOException {
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
			AsyncSupplier<ZippedFile, IOException> read = ZipArchiveRecords.readCentralDirectoryEntry(input);
			if (read.isDone()) {
				if (read.hasError()) throw read.getError();
				if (read.isCancelled()) return null;
				listener.centralDirectoryEntryFound(read.getResult());
				pos += read.getResult().headerLength - 4;
				return null;
			}
			Async<IOException> sp = new Async<>();
			read.thenStart("Scan ZIP", input.getPriority(), () -> {
				listener.centralDirectoryEntryFound(read.getResult());
				pos += read.getResult().headerLength - 4;
				sp.unblock();
			}, sp);
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
	
	private IAsync<IOException> skipRecordWithSize(int size) throws IOException {
		byte[] b = new byte[size];
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(b));
		if (r.isDone()) {
			if (r.hasError()) throw r.getError();
			long s;
			switch (size) {
			default:
			case 2: s = DataUtil.Read16U.LE.read(b, 0); break;
			case 4: s = DataUtil.Read32U.LE.read(b, 0); break;
			case 8: s = DataUtil.Read64.LE.read(b, 0); break;
			}
			pos += size + s;
			return skip(s);
		}
		Async<IOException> sp = new Async<>();
		r.onDone(() -> {
			long s;
			switch (size) {
			default:
			case 2: s = DataUtil.Read16U.LE.read(b, 0); break;
			case 4: s = DataUtil.Read32U.LE.read(b, 0); break;
			case 8: s = DataUtil.Read64.LE.read(b, 0); break;
			}
			pos += size + s;
			AsyncSupplier<Long, IOException> skip = input.skipAsync(s);
			if (skip.isDone()) {
				if (skip.hasError()) sp.error(skip.getError());
				else sp.unblock();
				return;
			}
			skip.onDone(sp);
		}, sp);
		return sp;
	}
	
	private IAsync<IOException>skip(long size) throws IOException {
		AsyncSupplier<Long, IOException> skip = input.skipAsync(size);
		if (skip.isDone()) {
			if (skip.hasError()) throw skip.getError();
			return null;
		}
		return skip;
	}
	
	private IAsync<IOException> readLocalFile() throws IOException {
		if (logger.debug())
			logger.debug("Zip scan: local entry found at "+(pos-4));
		// Local File Header
		AsyncSupplier<ZippedFile, IOException> readEntry = ZipArchiveRecords.readLocalFileEntry(input, true, pos - 4);
		if (!readEntry.isDone()) {
			Async<IOException> sp = new Async<>();
			readEntry.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
				try { readLocalFile(readEntry.getResult(), sp); }
				catch (IOException e) { sp.error(e); }
			}, sp);
			return sp;
		}
		if (readEntry.hasError())
			throw readEntry.getError();
		return readLocalFile(readEntry.getResult(), null);
	}
	
	private IAsync<IOException> readLocalFile(ZippedFile entry, Async<IOException> sp) throws IOException {
		Consumer<IO.Readable> uncompress = listener.entryFound(entry, pos);
		pos += entry.headerLength - 4;
		if (entry.isEcrypted()) {
			// we should have a 12-bytes encryption header before the data
			AsyncSupplier<Long, IOException> skip = input.skipAsync(12);
			pos += 12;
			if (!skip.isDone()) {
				Async<IOException> sp2 = sp == null ? new Async<>() : sp;
				skip.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
					try { readLocalFile(entry, sp2, uncompress); }
					catch (IOException e) { sp2.error(e); }
				}, sp2);
				return sp;
			}
			if (skip.hasError())
				throw skip.getError();
		}
		return readLocalFile(entry, sp, uncompress);
	}
	
	private IAsync<IOException> readLocalFile(ZippedFile entry, Async<IOException> sp, Consumer<IO.Readable> uncompress) throws IOException {
		if (uncompress == null) {
			// skip
			if ((entry.flags & 8) == 0) {
				AsyncSupplier<Long, IOException> skip = input.skipAsync(entry.compressedSize);
				pos += entry.compressedSize;
				if (!skip.isDone()) {
					if (sp == null)
						return skip;
					skip.onDone(sp);
					return sp;
				}
				if (sp != null)
					sp.unblock();
				return sp;
			}
			// search following data descriptor
			AsyncSupplier<Boolean, IOException> search = searchPKType(0x0708);
			if (search.isDone()) {
				if (search.hasError())
					throw search.getError();
				return readLocalFileDataDescriptor(entry, search, sp);
			}
			Async<IOException> sp2 = sp != null ? sp : new Async<>();
			search.thenStart("Scan ZIP", input.getPriority(), () -> {
				try { readLocalFileDataDescriptor(entry, search, sp2); }
				catch (IOException e) { sp2.error(e); }
			}, sp2);
			return sp2;
		}
		
		if ((entry.flags & 8) == 0) {
			uncompress.accept(entry.uncompressInline(input));
			return sp;
		}
		// search following data descriptor
		AsyncSupplier<Boolean, IOException> search = searchPKType(0x0708);
		if (search.isDone()) {
			if (search.hasError())
				throw search.getError();
			IAsync<IOException> spRead = readLocalFileDataDescriptor(entry, search, null);
			if (spRead == null) {
				callUncompress(uncompress, entry);
				sp.unblock();
				return sp;
			}
			Async<IOException> sp2 = sp != null ? sp : new Async<>();
			spRead.thenStart("Scan ZIP", input.getPriority(), () -> {
				try {
					callUncompress(uncompress, entry);
					sp2.unblock();
				} catch (IOException e) {
					sp2.error(e);
				}
			}, sp2);
			return sp2;
		}
		Async<IOException> sp2 = sp != null ? sp : new Async<>();
		search.thenStart("Scan ZIP", input.getPriority(), () -> {
			IAsync<IOException> spRead;
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
			spRead.thenStart("Scan ZIP", input.getPriority(), () -> {
				try {
					callUncompress(uncompress, entry);
					sp2.unblock();
				} catch (IOException e) {
					sp2.error(e);
				}
			}, sp2);
		}, sp2);
		return sp2;
	}
	
	private void callUncompress(Consumer<IO.Readable> uncompress, ZippedFile entry) throws IOException {
		if (input instanceof IO.Readable.Seekable) {
			((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, entry.offset+entry.headerLength);
			uncompress.accept(entry.uncompressInline(input));
			((IO.Readable.Seekable)input).seekSync(SeekType.FROM_BEGINNING, pos);
		} else
			uncompress.accept(null);
	}
	
	private IAsync<IOException> readLocalFileDataDescriptor(ZippedFile entry, AsyncSupplier<Boolean, IOException> search, Async<IOException> sp) throws IOException {
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
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(buf));
		if (r.isDone()) {
			if (r.hasError())
				throw r.getError();
			readLocalFileDataDescriptor(entry, buf);
			if (sp != null)
				sp.unblock();
			return sp;
		}
		Async<IOException> sp2 = sp != null ? sp : new Async<>();
		r.thenStart("Scan ZIP", input.getPriority(), () -> {
			readLocalFileDataDescriptor(entry, buf);
			sp2.unblock();
		}, sp2);
		return sp2;
	}

	private void readLocalFileDataDescriptor(ZippedFile entry, byte[] buf) {
		entry.crc32 = DataUtil.Read32U.LE.read(buf, 0);
		entry.compressedSize = DataUtil.Read32U.LE.read(buf, 4);
		entry.uncompressedSize = DataUtil.Read32U.LE.read(buf, 8);
		pos += 12;
	}
	
}
