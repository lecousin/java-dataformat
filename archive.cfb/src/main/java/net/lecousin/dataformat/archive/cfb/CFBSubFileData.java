package net.lecousin.dataformat.archive.cfb;

import java.io.IOException;

import net.lecousin.dataformat.archive.cfb.CFBFile.CFBSubFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.FragmentedSubIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
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
	public ILocalizableString getName() {
		return new FixedLocalizedString(file.name);
	}
	@Override
	public ILocalizableString getDescription() {
		return new FixedLocalizedString(file.name);
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
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<IO.Readable, IOException> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<CFBFile>,Exception> get = CFBDataFormat.cache.open(cfbData, this, priority/*, false*/, null, 0);
		get.onDone(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(IO.error(get.getError()));
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
		sp.onCancel(get::unblockCancel);
		return sp;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		return null;
	}
}
