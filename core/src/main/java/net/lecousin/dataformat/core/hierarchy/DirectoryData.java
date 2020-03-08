package net.lecousin.dataformat.core.hierarchy;

import java.io.IOException;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableStringBuffer;

public class DirectoryData extends Data implements IDirectoryData {

	public DirectoryData(Data parent, ContainerDataFormat containerFormat, ILocalizableString name) {
		this.parent = parent;
		this.name = name;
		setFormat(new DirectoryDataFormat(containerFormat));
	}

	protected Data parent;
	protected ILocalizableString name;
	
	@Override
	public ILocalizableString getName() { return name; }

	@Override
	public ILocalizableString getDescription() { return new LocalizableStringBuffer(parent.getDescription(), "/", name); }

	@Override
	public long getSize() { return 0; }

	@Override
	public boolean hasContent() { return false; }

	@Override
	public Data getContainer() { return parent; }
	
	@Override
	protected AsyncSupplier<Readable, IOException> openIOReadOnly(Priority priority) {
		return null;
	}

	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}

	@Override
	protected <T extends Seekable & net.lecousin.framework.io.IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		return null;
	}
	
}
