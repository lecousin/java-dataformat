package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class MountedDrivesData extends Data {

	public MountedDrivesData(Data parent) {
		this.parent = parent;
		setFormat(MountedDrivesDataFormat.instance);
	}
	
	protected Data parent;
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Mounted drives"); // TODO
	}

	@Override
	public ILocalizableString getDescription() {
		return new FixedLocalizedString("");
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
