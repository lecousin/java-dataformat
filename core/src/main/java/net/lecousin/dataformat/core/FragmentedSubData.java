package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.FragmentedSubIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.LinkedIO.Readable.Seekable.Buffered.DeterminedSize;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;

public class FragmentedSubData extends Data {

	public FragmentedSubData(Data parent, String name) {
		this.parent = parent;
		this.name = name;
	}
	
	protected Data parent;
	protected String name;
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
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
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
				io = new FragmentedSubIO.Readable((IO.Readable.Seekable)io, fragments, true, name);
				if (header != null)
					io = new DeterminedSize(name, new ByteArrayIO(header, "header"), (FragmentedSubIO.Readable)io);
				result.unblockSuccess(io);
			}
		});
		return result;
	}
	
	
	
}
