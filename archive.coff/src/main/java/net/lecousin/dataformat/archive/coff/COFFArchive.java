package net.lecousin.dataformat.archive.coff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.progress.WorkProgress;

public class COFFArchive {

	public static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(COFFArchive.class);
	}
	
	public static class COFFFile {
		public String name;
		public long size;
		public long offset;
	}
	
	private Async<IOException> contentReady = null;
	private ArrayList<COFFFile> content = new ArrayList<>();
	
	public Async<IOException> contentReady() {
		return contentReady;
	}
	
	public ArrayList<COFFFile> getContent() {
		return content;
	}
	
	/**
	 * Scan the content of the archive (list of files).
	 * It does not close the given IO.
	 * To know when the content is ready, use the SynchronizationPoint returned by contentReady()
	 * @param io
	 * @param list optional
	 */
	public void scanContent(IO.Readable io, AsyncCollection<COFFFile> list, WorkProgress progress, long work) {
		contentReady = new Async<>();
		if (list != null)
			contentReady.onDone(new Runnable() {
				@Override
				public void run() {
					list.done();
				}
			});
		// check header
		ByteBuffer buf = ByteBuffer.allocate(60);
		buf.limit(8);
		io.readFullyAsync(buf).listen(new Listener<Integer, IOException>() {
			@Override
			public void ready(Integer nbRead) {
				if (nbRead.intValue() != 8) {
					contentReady.error(new IOException("Not a COFF Archive"));
					return;
				}
				buf.flip();
				if (buf.get() != '!' ||
					buf.get() != '<' ||
					buf.get() != 'a' ||
					buf.get() != 'r' ||
					buf.get() != 'c' ||
					buf.get() != 'h' ||
					buf.get() != '>' ||
					buf.get() != '\n') {
					contentReady.error(new IOException("Not a COFF Archive"));
					return;
				}
				readEntry(io, buf, 8, list, progress, work);
			}
			@Override
			public void error(IOException error) {
				contentReady.error(error);
			}
			@Override
			public void cancelled(CancelException event) {
				contentReady.cancel(event);
			}
		});
	}
	private void readEntry(IO.Readable io, ByteBuffer buf, long pos, AsyncCollection<COFFFile> list, WorkProgress progress, long work) {
		buf.clear();
		io.readFullyAsync(buf).listen(new Listener<Integer, IOException>() {
			@Override
			public void ready(Integer nbRead) {
				if (nbRead.intValue() <= 0) {
					if (progress != null) progress.progress(work);
					contentReady.unblock();
					return;
				}
				if (nbRead.intValue() < 60) {
					contentReady.error(new IOException("Invalid COFF Archive: only "+nbRead.intValue()+" bytes read for a file header at position "+pos));
					return;
				}
				COFFFile file = new COFFFile();
				file.name = new String(buf.array(), 0, 16).trim();
				file.size = new Long(new String(buf.array(), 48, 10).trim()).longValue();
				file.offset = pos+60;
				content.add(file);
				if (list != null)
					list.newElements(Collections.singletonList(file));
				long s = file.size + (file.size%2);
				if (progress != null) progress.progress(work/10);
				io.skipAsync(s).listen(new Listener<Long, IOException>() {
					@Override
					public void ready(Long r) {
						readEntry(io, buf, pos+60+s, list, progress, work-work/10);
					}
					@Override
					public void error(IOException error) {
						contentReady.error(error);
					}
					@Override
					public void cancelled(CancelException event) {
						contentReady.cancel(event);
					}
				});
			}
			@Override
			public void error(IOException error) {
				contentReady.error(error);
			}
			@Override
			public void cancelled(CancelException event) {
				contentReady.cancel(event);
			}
		});
	}
	
}
