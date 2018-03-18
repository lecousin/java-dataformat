package net.lecousin.dataformat.archive.cfb;

import net.lecousin.dataformat.archive.cfb.CFBFile.CFBSubFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.FragmentedSubIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.memory.CachedObject;

public class CFBSubFileData extends Data {

	CFBSubFileData(Data cfb, CFBSubFile file) {
		this.cfbData = cfb;
		this.file = file;
	}
	
	private Data cfbData;
	private CFBSubFile file;
	
	@Override
	public String getName() {
		return file.name;
	}
	@Override
	public String getDescription() {
		return file.name;
	}
	@Override
	public long getSize() {
		long size = 0;
		for (RangeLong r : file.fragments)
			size += r.max - r.min + 1;
		return size;
	}
	
	@Override
	public boolean hasContent() {
		return true;
	}
	@Override
	public Data getContainer() {
		return cfbData;
	}
	
	@SuppressWarnings("resource")
	@Override
	protected AsyncWork<IO.Readable, Exception> openIOReadOnly(byte priority) {
		AsyncWork<IO.Readable, Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<CFBFile>,Exception> get = CFBDataFormat.cache.open(cfbData, this, priority/*, false*/, null, 0);
		get.listenInline(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				CachedObject<CFBFile> cfb = get.getResult();
				FragmentedSubIO.Readable io = new FragmentedSubIO.Readable(cfb.get().io, file.fragments, false, file.name);
				io.addCloseListener(new Runnable() {
					@Override
					public void run() {
						cfb.release(CFBSubFileData.this);
					}
				});
				sp.unblockSuccess(io);
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				get.unblockCancel(event);
			}
		});
		return sp;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		return null;
	}
}
