package net.lecousin.dataformat.core.file;

import java.io.File;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.memory.SimpleCache;
import net.lecousin.framework.util.Provider.FromValue;

public class FileData extends Data {

	private static SimpleCache<File,FileData> cache;
	static {
		cache = new SimpleCache<>("FileData", new FromValue<File, FileData>() {
			@Override
			public FileData provide(File file) {
				return new FileData(file);
			}
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
	
	private FileData(File file) {
		this.file = file;
		this.isDirectory = file.isDirectory();
		if (this.isDirectory)
			setFormat(FileSystemDirectoryFormat.instance);
	}
	
	File file;
	boolean isDirectory;
	
	public File getFile() { return file; }
	@Override
	public String getName() {
		return file.getName();
	}
	@Override
	public String getDescription() {
		return file.getAbsolutePath();
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
	protected AsyncWork<IO,? extends Exception> openIO(byte priority) {
		if (file.isDirectory()) return null;
		FileIO.ReadOnly io = new FileIO.ReadOnly(file, priority);
		AsyncWork<IO,Exception> sp = new AsyncWork<>();
		io.canStart().listenInline(new Runnable() {
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
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				io.canStart().cancel(event);
			}
		});
		return sp;
	}
	
}
