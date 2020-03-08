package net.lecousin.dataformat.archive.tar;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;


public class TarFile implements Closeable {

	public TarFile(IO.Readable.Seekable input, WorkProgress progress, long work) {
		this.input = input;
		readEntries(progress, work);
	}
	
	private IO.Readable.Seekable input;
	private ArrayList<TarEntry> entries = new ArrayList<TarEntry>();
	private Async<IOException> ready = new Async<IOException>();
	
	@Override
	public void close() throws IOException {
		try { input.close(); }
		catch (Exception e) { throw IO.error(e); }
		input = null;
	}
	
	public IAsync<IOException> getSynchOnReady() {
		return ready;
	}
	
	public ArrayList<TarEntry> getEntries() {
		return entries;
	}
	
	private void readEntries(WorkProgress progress, long work) {
		byte[] buffer = new byte[512];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		readEntry(buffer, bb, 0, progress, work);
	}
	private void readEntry(byte[] buffer, ByteBuffer bb, long pos, WorkProgress progress, long work) {
		bb.clear();
		AsyncSupplier<Integer,IOException> readHeader = input.readFullyAsync(pos, bb);
		readHeader.listen(new Listener<Integer, IOException>() {
			@Override
			public void ready(Integer nbRead) {
				if (nbRead.intValue() <= 0) {
					// end of file
					if (progress != null) progress.progress(work);
					ready.unblock();
					return;
				}
				if (nbRead.intValue() != 512) {
					ready.error(new IOException("TAR file "+input.getSourceDescription()+" is truncated: only "+nbRead.intValue()+" bytes read at "+pos+", expecting is at least 512 for a file header in a TAR file"));
					return;
				}
				Task.cpu("Read TAR file entry at "+pos, Priority.NORMAL, new ReadEntry(buffer, bb, pos, progress, work)).start();
			}
			@Override
			public void error(IOException error) {
				ready.error(error);
			}
			@Override
			public void cancelled(CancelException event) {
				ready.cancel(event);
			}
		});
	}
	
	private class ReadEntry implements Executable<Void,NoException> {
		public ReadEntry(byte[] buffer, ByteBuffer bb, long pos, WorkProgress progress, long work) {
			this.buffer = buffer;
			this.bb = bb;
			this.pos = pos;
			this.progress = progress;
			this.work = work;
		}
		private byte[] buffer;
		private ByteBuffer bb;
		private long pos;
		private WorkProgress progress;
		private long work;
		@Override
		public Void execute(Task<Void, NoException> taskContext) {
			if (progress != null) progress.progress(work/10);
			work -= work/10;
			String name = readString(buffer, 0, 100);
			if (name == null) {
				if (progress != null) progress.progress(work);
				ready.unblock();
				return null;
			};
			boolean isDirectory = name.charAt(name.length()-1) == '/';
			if (isDirectory) name = name.substring(0, name.length()-1);
			String s = new String(buffer, 124, 11).trim();
			int size;
			try {
				size = Integer.parseInt(s, 8);
			} catch (NumberFormatException e) {
				ready.error(new IOException("Invalid TAR file "+input.getSourceDescription()+": size stored at "+(pos+124)+" is not a valid octal number"));
				return null;
			}
			entries.add(new TarEntry(TarFile.this, pos+512, name, size, isDirectory));
			pos += 512 + size;
			if ((size%512) > 0) pos += 512-(size%512);
			readEntry(buffer, bb, pos, progress, work);
			return null;
		}
	}
	
	private static String readString(byte[] buffer, int off, int len) {
		int i;
		for (i = off; i < off+len; ++i) if (buffer[i] == 0) break;
		if (i == 0) return null;
		return new String(buffer, off, i-off);
	}
	
}
