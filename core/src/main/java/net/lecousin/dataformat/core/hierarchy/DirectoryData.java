package net.lecousin.dataformat.core.hierarchy;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Readable.Seekable;

public class DirectoryData extends Data {

	public DirectoryData(Data parent, ContainerDataFormat containerFormat, String name) {
		this.parent = parent;
		this.name = name;
		setFormat(new DirectoryDataFormat(containerFormat));
	}

	protected Data parent;
	protected String name;
	
	@Override
	public String getName() { return name; }

	@Override
	public String getDescription() { return parent.getDescription() + '/' + name; }

	@Override
	public long getSize() { return 0; }

	@Override
	public boolean hasContent() { return false; }

	@Override
	public Data getContainer() { return parent; }
	
	@Override
	protected AsyncWork<Readable, ? extends Exception> openIOReadOnly(byte priority) {
		return null;
	}

	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}

	@Override
	protected <T extends Seekable & net.lecousin.framework.io.IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		return null;
	}
	
}
