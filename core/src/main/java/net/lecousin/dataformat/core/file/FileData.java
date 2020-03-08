package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.hierarchy.IDirectoryData;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.memory.SimpleCache;

public class FileData extends Data {
	
	public static class DirectoryData extends FileData implements IDirectoryData {
		private DirectoryData(File file) {
			super(file, true);
		}
	}

	private static SimpleCache<File,FileData> cache;
	static {
		cache = new SimpleCache<>("FileData", file -> {
			boolean dir = file.isDirectory();
			if (dir)
				return new DirectoryData(file);
			return new FileData(file, false);
		});
		LCCore.get().toClose(cache);
	}
	
	public static FileData get(File file) {
		if (file == null) throw new IllegalArgumentException("Given file is null");
		return cache.get(file);
	}
	
	public void removeFromCache() {
		cache.remove(file);
	}
	@Override
	public void releaseEverything() {
		super.releaseEverything();
		removeFromCache();
	}
	
	protected FileData(File file, boolean isDirectory) {
		this.file = file;
		this.isDirectory = isDirectory;
		if (this.isDirectory)
			setFormat(FileSystemDirectoryFormat.instance);
	}
	
	File file;
	boolean isDirectory;
	
	public File getFile() { return file; }
	@Override
	public FixedLocalizedString getName() {
		return new FixedLocalizedString(file.getName());
	}
	@Override
	public FixedLocalizedString getDescription() {
		return new FixedLocalizedString(file.getAbsolutePath());
	}
	@Override
	public long getSize() {
		return file.length();
	}
	
	@Override
	public boolean hasContent() {
		return !isDirectory;
	}
	@Override
	public Data getContainer() {
		File p = file.getParentFile();
		if (p == null) return null;
		return FileData.get(p);
	}
	
	@SuppressWarnings("resource")
	@Override
	protected AsyncSupplier<IO.Readable,IOException> openIOReadOnly(Priority priority) {
		if (file.isDirectory()) return null;
		FileIO.ReadOnly io = new FileIO.ReadOnly(file, priority);
		AsyncSupplier<IO.Readable, IOException> sp = new AsyncSupplier<>();
		io.canStart().onDone(new Runnable() {
			@Override
			public void run() {
				if (io.canStart().isCancelled())
					sp.unblockCancel(io.canStart().getCancelEvent());
				else if (io.canStart().isSuccessful())
					sp.unblockSuccess(io);
				else {
					io.closeAsync();
					sp.unblockError(io.canStart().getError());
				}
			}
		});
		sp.onCancel(event -> io.canStart().cancel(event));
		return sp;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return !file.isDirectory();
	}
	
	@SuppressWarnings("resource")
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		FileIO.ReadWrite io = new FileIO.ReadWrite(file, priority);
		AsyncSupplier<T, IOException> sp = new AsyncSupplier<>();
		io.canStart().onDone(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				if (io.canStart().isCancelled())
					sp.unblockCancel(io.canStart().getCancelEvent());
				else if (io.canStart().isSuccessful())
					sp.unblockSuccess((T)io);
				else {
					io.closeAsync();
					sp.unblockError(io.canStart().getError());
				}
			}
		});
		sp.onCancel(event -> io.canStart().cancel(event));
		return sp;
	}
	
}
