package net.lecousin.dataformat.compress.gzip;

import net.lecousin.compression.gzip.GZipReadable;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;

public class GZippedData extends Data {

	GZippedData(Data container, GZipHeader header) {
		this.container = container;
		this.header = header;
	}
	
	protected Data container;
	protected GZipHeader header;
	
	@Override
	public String getName() { return header.filename != null ? header.filename : ""; }

	@Override
	public String getDescription() { return "GZipped"; }

	@Override
	public long getSize() { return -1; }

	@Override
	public boolean hasContent() { return true; }

	@Override
	public Data getContainer() { return container; }

	@Override
	protected AsyncWork<IO, ? extends Exception> openIO(byte priority) {
		AsyncWork<IO, Exception> result = new AsyncWork<>();
		container.open(priority).listenInline((io) -> {
			result.unblockSuccess(new GZipReadable(io, priority));
		}, result);
		return result;
	}

}
