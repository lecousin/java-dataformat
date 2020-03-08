package net.lecousin.dataformat.filesystem;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AccessDeniedException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.remotejvm.RemoteJVM;
import net.lecousin.framework.system.hardware.Drive;
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
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<IO.Readable, IOException> result = new AsyncSupplier<>();
		Task.unmanaged("Opening physical drive for reading", priority, t -> {
			try {
				result.unblockSuccess(Drives.getInstance().openReadOnly(drive, priority));
			} catch (AccessDeniedException e) {
				AsyncSupplier<RemoteJVM, Exception> start = RemoteJVM.getElevatedJVM();
				start.onDone(() -> {
					if (start.hasError()) {
						result.error(IO.error(start.getError()));
						return;
					}
					RemoteJVM jvm = start.getResult();
					AsyncSupplier<Object, Exception> open;
					try {
						open = jvm.getConnection().callStatic(OpenDiskFromRemote.class.getMethod("openDisk", String.class), drive.getOSId());
					} catch (Exception err) {
						result.error(IO.error(err));
						return;
					}
					open.onDone(() -> {
						if (open.hasError()) {
							result.error(IO.error(open.getError()));
							return;
						}
						AsyncSupplier<IO.Readable.Seekable, Exception> remoteOpen = (AsyncSupplier<IO.Readable.Seekable, Exception>)open.getResult();
						remoteOpen.onDone(() -> {
							if (remoteOpen.hasError()) {
								result.error(IO.error(remoteOpen.getError()));
								return;
							}
							IO.Readable.Seekable io = remoteOpen.getResult();
							result.unblockSuccess(io);
						});
					});
				});
			} catch (IOException e) {
				result.error(e);
			}
			return null;
		}).start();
		return result;
	}

	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}

	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		return null;
	}

	public static class OpenDiskFromRemote {
		
		public static AsyncSupplier<IO.Readable.Seekable, Exception> openDisk(String diskId) {
			AsyncSupplier<IO.Readable.Seekable, Exception> result = new AsyncSupplier<>();
			Drives.getInstance().initialize().getSynch().thenStart(Task.unmanaged("Opening physical drive for reading", Priority.NORMAL, t -> {
				for (Drive drive : Drives.getInstance().getDrives()) {
					if (!(drive instanceof PhysicalDrive)) continue;
					PhysicalDrive d = (PhysicalDrive)drive;
					if (!diskId.equals(d.getOSId())) continue;
					try {
						@SuppressWarnings("resource")
						IO.Readable.Seekable io = Drives.getInstance().openReadOnly(d, Priority.NORMAL);
						result.unblockSuccess(io);
					} catch (IOException e) {
						result.error(e);
					}
					return null;
				}
				result.error(new Exception("Drive " + diskId + " not found"));
				return null;
			}), result);
			return result;
		}
		
	}
	
}
