package net.lecousin.dataformat.archive.cab;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.EmptyReadable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;

public class CabFileData extends Data {

	CabFileData(Data cab, CabFile.File file) {
		this.cab = cab;
		this.file = file;
	}
	
	private Data cab;
	private CabFile.File file;
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString(file.getName());
	}
	@Override
	public ILocalizableString getDescription() {
		return new FixedLocalizedString(file.getName());
	}
	@Override
	public long getSize() {
		return file.getSize();
	}
	@Override
	public boolean hasContent() {
		return true;
	}
	@Override
	public Data getContainer() {
		return cab;
	}
	@SuppressWarnings("resource")
	@Override
	protected AsyncWork<IO.Readable, Exception> openIOReadOnly(byte priority) {
		if (file.getSize() == 0)
			return new AsyncWork<>(new EmptyReadable(file.getName(), priority),null);
		AsyncWork<IO.Readable, Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<CabFile>, Exception> c = CabDataFormat.cache.open(cab, this, priority, null, 0);
		c.listenInline(new Runnable() {
			@Override
			public void run() {
				if (c.hasError()) { sp.error(c.getError()); return; }
				if (c.isCancelled()) { sp.cancel(c.getCancelEvent()); return; }
				CabFile cab = c.getResult().get();
				
				AsyncWork<IO.Readable,IOException> open = cab.openFile(file, priority);
				open.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!open.isSuccessful()) {
							if (open.hasError()) sp.error(open.getError());
							else sp.cancel(open.getCancelEvent());
							c.getResult().release(CabFileData.this);
							return;
						}
						IO.Readable io = open.getResult();
						io.addCloseListener(new Runnable() {
							@Override
							public void run() {
								c.getResult().release(CabFileData.this);
							}
						});
						sp.unblockSuccess(io);
					}
				});
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
