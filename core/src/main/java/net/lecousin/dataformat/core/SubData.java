package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;

public class SubData extends Data {

	public SubData(Data parent, long offset, long size, String name) {
		this(parent, offset, size, name, null);
	}
	public SubData(Data parent, long offset, long size, String name, String path) {
		this.parent = parent;
		this.offset = offset;
		this.size = size;
		this.name = name;
		this.path = path;
	}
	
	protected Data parent;
	protected long offset;
	protected long size;
	protected String name;
	protected String path;
	
	public Data getParent() { return parent; }
	public long getOffset() { return offset; }

	@Override
	public String getName() {
		return name;
	}
	
	public String getDirectoryPath() {
		return path;
	}

	@Override
	public String getDescription() {
		return name;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public boolean hasContent() {
		return true;
	}
	@Override
	public Data getContainer() {
		return parent;
	}

	@Override
	protected AsyncWork<IO, ? extends Exception> openIO(byte priority) {
		AsyncWork<IO,Exception> result = new AsyncWork<>();
		AsyncWork<? extends IO,Exception> open = parent.open(priority);
		open.listenInline(new Runnable() {
			@SuppressWarnings("resource")
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled())
						result.unblockCancel(open.getCancelEvent());
					else
						result.unblockError(open.getError());
					return;
				}
				IO io = open.getResult();
				result.unblockSuccess(new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)io, offset, size, name, true));
			}
		});
		return result;
	}
	
	
	
}
