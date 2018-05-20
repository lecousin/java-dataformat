package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		AsyncWork<IO.Readable, Exception> result = new AsyncWork<>();
		AsyncWork<? extends IO.Readable, Exception> open = parent.openReadOnly(priority);
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
				name.appLocalization().listenInline((localizedName) -> {
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
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		AsyncWork<T, Exception> result = new AsyncWork<>();
		AsyncWork<T, ? extends Exception> open = parent.openReadWrite(priority);
		open.listenInline(new Runnable() {
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
				name.appLocalization().listenInline((localizedName) -> {
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
