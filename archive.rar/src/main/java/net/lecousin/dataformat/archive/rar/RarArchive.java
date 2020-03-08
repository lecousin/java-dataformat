package net.lecousin.dataformat.archive.rar;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.progress.WorkProgress;

public class RarArchive {
	
	static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(RarArchive.class);
	}

	public RarArchive(IO.Readable.Seekable io) {
		this.io = io;
	}
	
	public static enum Format {
		/** 1.4 */
		_14,
		/** 1.5 to 4.x */
		_15,
		/** 5.x */
		_5
	}
	
	IO.Readable.Seekable io;
	Async<IOException> contentLoaded = null;
	Format format = null;
	ArrayList<RARFile> content = new ArrayList<>();
	
	boolean isVolumeArchive;
	boolean isLocked;
	boolean isSolid;
	
	public class RARFile {
		long compressedSize;
		long uncompressedSize;
		int method;
		String name;

		public long getCompressedSize() {
			return compressedSize;
		}
		public long getUncompressedSize() {
			return uncompressedSize;
		}
		public String getName() {
			return name;
		}
	}
	
	public Async<IOException> loadContent(WorkProgress progress, long work) {
		if (contentLoaded != null)
			return contentLoaded;
		contentLoaded = new Async<>();
		RarLoader.load(this, progress, work);
		return contentLoaded;
	}
	
	public ArrayList<RARFile> getContent() {
		return content;
	}
	
	public void close(boolean asynch) {
		if (!contentLoaded.isDone())
			contentLoaded.cancel(new CancelException("RarArchive closed"));
		content = null;
		if (asynch)
			io.closeAsync();
		else
			try { io.close(); } catch (Throwable t) {}
		io = null;
	}
	
}
