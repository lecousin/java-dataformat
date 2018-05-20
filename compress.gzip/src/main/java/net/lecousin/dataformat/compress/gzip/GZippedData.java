package net.lecousin.dataformat.compress.gzip;

import net.lecousin.compression.gzip.GZipReadable;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		AsyncWork<IO.Readable, Exception> result = new AsyncWork<>();
		container.openReadOnly(priority).listenInline((io) -> {
			result.unblockSuccess(new GZipReadable(io, priority));
		}, result);
		return result;
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
