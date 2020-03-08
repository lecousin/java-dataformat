package net.lecousin.dataformat.compress.gzip;

import java.io.IOException;

import net.lecousin.compression.gzip.GZipReadable;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;

public class GZippedData extends Data {

	GZippedData(Data container, GZipHeader header) {
		this.container = container;
		this.header = header;
	}
	
	protected Data container;
	protected GZipHeader header;
	
	@Override
	public FixedLocalizedString getName() { return new FixedLocalizedString(header.filename != null ? header.filename : ""); }

	@Override
	public FixedLocalizedString getDescription() { return new FixedLocalizedString("GZipped"); }

	@Override
	public long getSize() { return -1; }

	@Override
	public boolean hasContent() { return true; }

	@Override
	public Data getContainer() { return container; }

	@Override
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<IO.Readable, IOException> result = new AsyncSupplier<>();
		container.openReadOnly(priority).onDone((io) -> {
			result.unblockSuccess(new GZipReadable(io, priority));
		}, result);
		return result;
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
