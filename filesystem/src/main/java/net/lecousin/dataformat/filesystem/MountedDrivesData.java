package net.lecousin.dataformat.filesystem;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
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
