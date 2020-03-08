package net.lecousin.dataformat.core;

import java.io.IOException;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.FragmentedSubIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.LinkedIO;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;

public class FragmentedSubData extends Data {

	public FragmentedSubData(Data parent, ILocalizableString name) {
		this.parent = parent;
		this.name = name;
	}
	
	protected Data parent;
	protected ILocalizableString name;
	protected byte[] header = null;
	protected FragmentedRangeLong fragments = new FragmentedRangeLong();
	
	public void addHeader(byte[] header) {
		this.header = header;
	}
	
	public void addFragment(RangeLong fragment) {
		fragments.add(fragment);
	}
	public void addFragment(long offset, long size) {
		addFragment(new RangeLong(offset, offset+size-1));
	}

	@Override
	public ILocalizableString getName() {
		return name;
	}

	@Override
	public ILocalizableString getDescription() {
		return name;
	}

	@Override
	public long getSize() {
		return fragments.getTotalSize();
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
				name.appLocalization().onDone((localizedName) -> {
					IO.Readable io = open.getResult();
					io = new FragmentedSubIO.Readable((IO.Readable.Seekable)io, fragments, true, localizedName);
					if (header != null)
						io = new LinkedIO.Readable.Seekable.DeterminedSize(localizedName, new ByteArrayIO(header, "header"), (FragmentedSubIO.Readable)io);
					result.unblockSuccess(io);
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
				name.appLocalization().onDone((localizedName) -> {
					T io = open.getResult();
					io = (T)new FragmentedSubIO.ReadWrite(io, fragments, true, localizedName);
					if (header != null)
						io = (T)new LinkedIO.ReadWrite(localizedName, new ByteArrayIO(header, "header"), (FragmentedSubIO.ReadWrite)io);
					result.unblockSuccess(io);
				});
			}
		});
		return result;
	}
	
}
