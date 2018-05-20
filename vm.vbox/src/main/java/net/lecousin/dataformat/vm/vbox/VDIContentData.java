package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		AsyncWork<IO.Readable, Exception> result = new AsyncWork<>();
		AsyncWork<CachedObject<VirtualBoxDiskImage>, Exception> get = VDIDataFormat.cache.open(vdi, this, priority, null, 0);
		get.listenInlineSP(() -> {
			@SuppressWarnings("resource")
			IO.Readable.Seekable io = get.getResult().get().createIO(priority);
			io.addCloseListener(() -> {
				get.getResult().release(VDIContentData.this);
			});
			result.unblockSuccess(io);
		}, result);
		return result;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		// TODO
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		// TODO
		return null;
	}
	
}
