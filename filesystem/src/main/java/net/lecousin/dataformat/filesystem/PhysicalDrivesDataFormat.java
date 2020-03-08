package net.lecousin.dataformat.filesystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.FakeWorkProgress;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.Drive;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.hardware.Drives.DriveListener;
import net.lecousin.framework.system.hardware.PhysicalDrive;
import net.lecousin.framework.ui.iconset.hardware.HardwareIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class PhysicalDrivesDataFormat implements ContainerDataFormat {

	public static final PhysicalDrivesDataFormat instance = new PhysicalDrivesDataFormat();
	
	private PhysicalDrivesDataFormat() {
	}
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.filesystem", "Physical drives");
	}

	@Override
	public IconProvider getIconProvider() {
		return HardwareIconSet.Icons.HARD_DISK_INTERNAL.get();
	}

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}

	@Override
	public AsyncSupplier<? extends DataFormatInfo, ?> getInfo(Data data, Priority priority) {
		return new AsyncSupplier<>(null, null);
	}

	private static Map<PhysicalDrive, PhysicalDriveData> drivesData = new HashMap<>();
	
	private static class MyListener implements DriveListener {
		private MyListener(Data parent, CollectionListener<Data> listener) {
			this.parent = parent;
			this.listener = listener;
			listener.elementsReady(new ArrayList<>(0));
		}
		private Data parent;
		private CollectionListener<Data> listener;
		@Override
		public void newDrive(Drive drive) {
			if (!(drive instanceof PhysicalDrive)) return;
			PhysicalDriveData data;
			synchronized (drivesData) {
				data = drivesData.get(drive);
				if (data == null) {
					data = new PhysicalDriveData(parent, (PhysicalDrive)drive);
					drivesData.put((PhysicalDrive)drive, data);
				}
			}
			listener.elementsAdded(Collections.singleton(data));
		}
		@Override
		public void driveRemoved(Drive drive) {
			if (!(drive instanceof PhysicalDrive)) return;
			PhysicalDriveData data;
			synchronized (drivesData) {
				data = drivesData.get(drive);
				if (data == null) return;
			}
			listener.elementsRemoved(Collections.singleton(data));
		}
		@Override
		public void newPartition(DiskPartition partition) {
		}
		@Override
		public void partitionRemoved(DiskPartition partition) {
		}
	}
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		if (Drives.getInstance() == null)
			return new FakeWorkProgress();
		WorkProgress progress = Drives.getInstance().initialize();
		Drives.getInstance().getDrivesAndListen(new MyListener(container, listener));
		return progress;
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
		Drives drives = Drives.getInstance();
		if (drives != null)
			for (DriveListener l : drives.getDriveListeners())
				if ((l instanceof MyListener) && ((MyListener)l).listener == listener) {
					drives.removeDriveListener(l);
					return;
				}
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return PhysicalDriveDataCommonProperties.class;
	}

	@Override
	public PhysicalDriveDataCommonProperties getSubDataCommonProperties(Data subData) {
		PhysicalDriveDataCommonProperties p = new PhysicalDriveDataCommonProperties();
		PhysicalDrive drive = ((PhysicalDriveData)subData).getDrive();
		p.size = drive.getSize();
		p.manufacturer = drive.getManufacturer();
		p.model = drive.getModel();
		p.version = drive.getVersion();
		p.serialNumber = drive.getSerialNumber();
		return p;
	}

}
