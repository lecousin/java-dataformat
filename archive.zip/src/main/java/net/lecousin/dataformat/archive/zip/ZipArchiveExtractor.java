package net.lecousin.dataformat.archive.zip;

import java.io.IOException;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.buffering.BufferedReverseIOReading;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.log.Logger;

class ZipArchiveExtractor {

	ZipArchiveExtractor(ZipArchive zip) {
		this.zip = zip;
		this.zip_io = (IO.Readable.Seekable)zip.io;
		this.reverse_io = new BufferedReverseIOReading((IO.Readable.Seekable&IO.KnownSize)zip_io, 65536);
		this.reverse_io.canStartReading().thenStart("Searching Zip end of central directory in "+zip.io.getSourceDescription(), zip.io.getPriority(), new SearchEndOfCentralDirectory(), true);
	}
	
	private ZipArchive zip;
	private BufferedReverseIOReading reverse_io;
	private IO.Readable.Seekable zip_io;
	AsyncSupplier<Void,Exception> done = new AsyncSupplier<>();
	
	private class SearchEndOfCentralDirectory implements Executable<Void,NoException> {

		@Override
		public Void execute(Task<Void, NoException> taskContext) {
			Logger logger = ZipArchive.getLogger();
			long start = System.nanoTime();
			// we start from the end of the file
			// first, we should find the end of central directory
			try {
				if (!search(reverse_io, ZipArchiveRecords.EndOfCentralDirectory.ID, 20000)) {
					done.unblockError(new Exception("No end of central directory found"));
					return null;
				}
				// skip the header
				reverse_io.read();
				reverse_io.read();
				reverse_io.read();
				reverse_io.read();
				// read record
				zip.endOfCentralDirectory = ZipArchiveRecords.EndOfCentralDirectory.read(reverse_io);
				if (zip.endOfCentralDirectory.needsZip64()) {
					boolean found = false;
					do {
						int type = searchNext(reverse_io);
						if (type < 0) break;
						switch (type) {
						case ZipArchiveRecords.Zip64EndOfCentralDirectoryLocator.ID:
							// TODO
							found = true;
							break;
						case ZipArchiveRecords.EndOfCentralDirectory.ZIP64_ID:
							// TODO
							found = true;
							break;
						}
					} while (!found);
					if (!found)
						throw new Exception("End of central directory values indicate we have a Zip64 format, but the Zip64 end of central directory record cannot be found");
				}
			} catch (Exception e) {
				done.unblockError(e);
				return null;
			}
			// read central directory
			if (zip.endOfCentralDirectory.centralDirectoryOffset >= reverse_io.getSize()) {
				done.unblockError(new Exception("Central Directory offset is behind the end of the file..."));
				return null;
			}
			reverse_io.stop();
			zip_io.seekAsync(SeekType.FROM_BEGINNING, zip.endOfCentralDirectory.centralDirectoryOffset).onDone(new Runnable() {
				@SuppressWarnings("resource")
				@Override
				public void run() {
					PreBufferedReadable io_buf;
					if (zip_io instanceof IO.KnownSize) {
						try {
							long size = ((IO.KnownSize)zip_io).getSizeSync();
							io_buf = new PreBufferedReadable(zip_io, size - zip.endOfCentralDirectory.centralDirectoryOffset, 1024, taskContext.getPriority(), 8192, taskContext.getPriority(), 8);
						} catch (IOException e) {
							done.unblockError(e);
							return;
						}
					} else
						io_buf = new PreBufferedReadable(zip_io, 1024, taskContext.getPriority(), 8192, taskContext.getPriority(), 8);
					Async<IOException> cd = ZipArchiveRecords.readCentralDirectory(io_buf, (entry) -> {
						zip.centralDirectory.add(entry);
					});
					cd.onDone(() -> {
						if (logger.debug())
							logger.debug("Zip analyzed in "+(System.nanoTime()-start)/1000000+"ms.");
						done.unblockSuccess(null);
					}, (error) -> {
						done.unblockError(error);
					}, (cancel) -> {
						done.cancel(cancel);
					});
				}
			});
			return null;
		}
	};
	
	private static int searchNext(BufferedReverseIOReading io) throws IOException {
		int c1 = io.readReverse();
		if (c1 == -1) return -1;
		int c2 = io.readReverse();
		if (c2 == -1) return -1;
		int back = -1;
		do {
			int c;
			if (back == -1) {
				c = io.readReverse();
				if (c == -1) return -1;
			} else {
				c = back;
				back = -1;
			}
			if (c != 'K') {
				c1 = c2;
				c2 = c;
				continue;
			}
			c = io.readReverse();
			if (c == -1) return -1;
			if (c != 'P') {
				c1 = c2;
				c2 = 'K';
				back = c;
				continue;
			}
			return (c2<<8)|c1;
		} while (true);
	}
	
	private static boolean search(BufferedReverseIOReading io, int id, int maxBytes) throws IOException {
		byte[] buf = new byte[3];
		int bufSize = 0;
		int done = 0;
		do {
			int c;
			if (bufSize == 0) {
				c = io.readReverse();
				if (c == -1) return false;
				done++;
			} else
				c = (buf[--bufSize])&0xFF;
			if (c != (id&0xFF)) continue;

			if (bufSize == 0) {
				c = io.readReverse();
				if (c == -1) return false;
				done++;
			} else
				c = (buf[--bufSize])&0xFF;
			if (c != (id&0xFF00)>>8) {
				buf[bufSize++] = (byte)c;
				continue;
			}
			
			if (bufSize == 0) {
				c = io.readReverse();
				if (c == -1) return false;
				done++;
			} else
				c = (buf[--bufSize])&0xFF;
			if (c != 'K') {
				buf[bufSize++] = (byte)c;
				buf[bufSize++] = (byte)((id&0xFF00)>>8);
				continue;
			}
			
			if (bufSize == 0) {
				c = io.readReverse();
				if (c == -1) return false;
				done++;
			} else
				c = (buf[--bufSize])&0xFF;
			if (c != 'P') {
				buf[bufSize++] = (byte)c;
				buf[bufSize++] = (byte)'K';
				buf[bufSize++] = (byte)((id&0xFF00)>>8);
				continue;
			}
			return true;
		} while (maxBytes < 0 || done < maxBytes);
		return false;
	}
	
}
