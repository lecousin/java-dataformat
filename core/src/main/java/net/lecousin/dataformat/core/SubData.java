package net.lecousin.dataformat.core;

import java.io.IOException;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.locale.ILocalizableString;

public class SubData extends Data {

	public SubData(Data parent, long offset, long size, ILocalizableString name) {
		this(parent, offset, size, name, null);
	}
	public SubData(Data parent, long offset, long size, ILocalizableString name, String path) {
		this.parent = parent;
		this.offset = offset;
		this.size = size;
		this.name = name;
		this.path = path;
	}
	
	protected Data parent;
	protected long offset;
	protected long size;
	protected ILocalizableString name;
	protected String path;
	
	public Data getParent() { return parent; }
	public long getOffset() { return offset; }

	@Override
	public ILocalizableString getName() {
		return name;
	}
	
	public String getDirectoryPath() {
		return path;
	}

	@Override
	public ILocalizableString getDescription() {
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
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<IO.Readable, IOException> result = new AsyncSupplier<>();
		AsyncSupplier<? extends IO.Readable, IOException> open = parent.openReadOnly(priority);
		open.onDone(new Runnable() {
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
				name.appLocalization().onDone((localizedName) -> {
					result.unblockSuccess(new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)io, offset, size, localizedName, true));
				});
			}
		});
		return result;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return parent.canOpenReadWrite();
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		AsyncSupplier<T, IOException> result = new AsyncSupplier<>();
		AsyncSupplier<T, IOException> open = parent.openReadWrite(priority);
		open.onDone(new Runnable() {
			@SuppressWarnings({ "resource", "unchecked" })
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled())
						result.unblockCancel(open.getCancelEvent());
					else
						result.unblockError(open.getError());
					return;
				}
				T io = open.getResult();
				name.appLocalization().onDone((localizedName) -> {
					result.unblockSuccess((T)new SubIO.ReadWrite(io, offset, size, localizedName, true));
				});
			}
		});
		return result;
	}
	
}
