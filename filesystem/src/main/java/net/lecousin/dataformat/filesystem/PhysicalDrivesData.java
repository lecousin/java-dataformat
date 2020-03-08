package net.lecousin.dataformat.filesystem;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;

public class PhysicalDrivesData extends Data {

	public PhysicalDrivesData(Data parent) {
		this.parent = parent;
		setFormat(PhysicalDrivesDataFormat.instance);
	}
	
	private Data parent;
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.filesystem", "Physical drives");
	}

	@Override
	public ILocalizableString getDescription() {
		return getName();
	}

	@Override
	public long getSize() {
		return 0;
	}

	@Override
	public boolean hasContent() {
		return false;
	}

	@Override
	public Data getContainer() {
		return parent;
	}

	@Override
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		return null;
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
