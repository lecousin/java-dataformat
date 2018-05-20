package net.lecousin.dataformat.filesystem;

import java.io.IOException;
import java.math.BigInteger;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.hardware.PhysicalDrive;

public class PhysicalDriveData extends Data {

	public PhysicalDriveData(Data parent, PhysicalDrive drive) {
		this.parent = parent;
		this.drive = drive;
	}
	
	private Data parent;
	private PhysicalDrive drive;
	
	public PhysicalDrive getDrive() {
		return drive;
	}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString(drive.getManufacturer() + " - " + drive.getModel());
	}

	@Override
	public ILocalizableString getDescription() {
		return new FixedLocalizedString(drive.getManufacturer() + " - " + drive.getModel() + " - " + drive.getVersion() + " - " + drive.getSerialNumber());
	}

	@Override
	public long getSize() {
		BigInteger s = drive.getSize();
		if (s == null) return 0;
		return s.longValue();
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
	protected AsyncWork<IO.Readable, IOException> openIOReadOnly(byte priority) {
		return new Task.Unmanaged<IO.Readable, IOException>("Opening physical drive for reading", priority) {
			@Override
			public IO.Readable run() throws IOException {
				return Drives.getInstance().openReadOnly(drive, priority);
			}
		}.start().getOutput();
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
