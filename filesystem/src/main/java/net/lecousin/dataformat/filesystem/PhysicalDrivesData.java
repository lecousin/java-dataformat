package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		return null;
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
