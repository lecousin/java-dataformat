package net.lecousin.dataformat.vm.vbox;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.memory.CachedObject;

public class VDIContentData extends Data {

	public VDIContentData(Data vdi, VirtualBoxDiskImage image) {
		this.vdi = vdi;
		this.size = image.getSize();
	}
	
	private Data vdi;
	private long size;
	
	@Override
	public ILocalizableString getName() { return new LocalizableString("dataformat.vm.vbox", "Disk Image"); }

	@Override
	public ILocalizableString getDescription() { return new LocalizableString("dataformat.vm.vbox", "Disk Image"); }

	@Override
	public long getSize() { return size; }

	@Override
	public boolean hasContent() {
		return true;
	}

	@Override
	public Data getContainer() {
		return vdi;
	}

	@Override
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<IO.Readable, IOException> result = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<VirtualBoxDiskImage>, Exception> get = VDIDataFormat.cache.open(vdi, this, priority, null, 0);
		get.onDone(() -> {
			@SuppressWarnings("resource")
			IO.Readable.Seekable io = get.getResult().get().createIO(priority);
			io.addCloseListener(() -> {
				get.getResult().release(VDIContentData.this);
			});
			result.unblockSuccess(io);
		}, result, IO::error);
		return result;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		// TODO
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		// TODO
		return null;
	}
	
}
