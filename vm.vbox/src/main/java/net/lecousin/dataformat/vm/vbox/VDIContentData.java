package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.memory.CachedObject;

public class VDIContentData extends Data {

	public VDIContentData(Data vdi, VirtualBoxDiskImage image) {
		this.vdi = vdi;
		this.size = image.getSize();
	}
	
	private Data vdi;
	private long size;
	
	@Override
	public String getName() { return "Disk Image"; }

	@Override
	public String getDescription() { return "Disk Image"; }

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
	protected AsyncWork<IO, ? extends Exception> openIO(byte priority) {
		AsyncWork<IO, Exception> result = new AsyncWork<>();
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
	
}
