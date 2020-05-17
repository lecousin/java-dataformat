package net.lecousin.dataformat.filesystem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.file.FileData;
import net.lecousin.dataformat.core.file.FileSystemDirectoryFormat;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.FakeWorkProgress;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.drive.DiskPartition;
import net.lecousin.framework.system.hardware.drive.Drive;
import net.lecousin.framework.system.hardware.drive.DriveListener;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.ui.iconset.hardware.HardwareIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class MountedDrivesDataFormat implements ContainerDataFormat {

	public static final MountedDrivesDataFormat instance = new MountedDrivesDataFormat();
	
	private MountedDrivesDataFormat() {
		Drives hwdrives = LCSystem.get().getHardware().getDrives();
		WorkProgress init = hwdrives.initialize();
		isInit = false;
		Task.unmanaged("Initialize mounted drives", Priority.NORMAL, t -> {
			if (isInit) return null;
			File[] roots = File.listRoots();			
			synchronized (partitions) {
				if (isInit) return null;
				for (File root : roots)
					partitions.add(root);
			}
			return null;
		}).start();
		DriveListener dl = new DriveListener() {
			@Override
			public void newDrive(Drive drive) {
				List<File> added = new LinkedList<File>();
				synchronized (partitions) {
					drives.add(drive);
					for (File p : drive.getMountPoints())
						if (!partitions.contains(p)) {
							partitions.add(p);
							added.add(p);
						}
				}
				if (!added.isEmpty())
					added(added);
			}
			
			@Override
			public void driveRemoved(Drive drive) {
				List<File> removed = new LinkedList<File>();
				synchronized (partitions) {
					drives.remove(drive);
					for (File p : drive.getMountPoints())
						if (partitions.remove(p))
							removed.add(p);
				}
				if (!removed.isEmpty())
					removed(removed);
			}
			
			@Override
			public void newPartition(DiskPartition partition) {
				File p = partition.mountPoint;
				if (p == null) return;
				synchronized (partitions) {
					if (partitions.contains(p)) return;
					partitions.add(p);
				}
				added(Collections.singletonList(p));
			}

			@Override
			public void partitionRemoved(DiskPartition partition) {
				File p = partition.mountPoint;
				if (p == null) return;
				synchronized (partitions) {
					if (!partitions.remove(p)) return;
				}
				removed(Collections.singletonList(p));
			}
		};
		init.getSynch().thenStart("Get drives", Priority.NORMAL, () -> {
			if (!init.getSynch().isSuccessful())
				return;
			synchronized (partitions) {
				isInit = true;
			}
			hwdrives.getDrivesAndListen(dl);
			List<File> removed = new LinkedList<>();
			synchronized (partitions) {
				for (Iterator<File> it = partitions.iterator(); it.hasNext(); ) {
					File p = it.next();
					if (!hasDrive(p)) {
						it.remove();
						removed.add(p);
					}
				}
			}
			if (!removed.isEmpty())
				removed(removed);
		}, true);
	}
	
	private boolean isInit = false;
	private List<File> partitions = new LinkedList<>();
	private List<Drive> drives = new LinkedList<>();
	private List<CollectionListener<Data>> listeners = new LinkedList<>();
	
	private boolean hasDrive(File partition) {
		for (Drive d : drives)
			for (File p : d.getMountPoints())
				if (p.equals(partition))
					return true;
		return false;
	}
	
	private void added(List<File> partitions) {
		List<Data> data = new ArrayList<>(partitions.size());
		for (File p : partitions) data.add(FileData.get(p));
		synchronized (listeners) {
			for (CollectionListener<Data> l : listeners)
				l.elementsAdded(data);
		}
	}
	
	private void removed(List<File> partitions) {
		List<Data> data = new ArrayList<>(partitions.size());
		for (File p : partitions) data.add(FileData.get(p));
		synchronized (listeners) {
			for (CollectionListener<Data> l : listeners)
				l.elementsRemoved(data);
		}
	}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Mounted drives"); // TODO
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
		return null;
	}

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		synchronized (partitions) {
			synchronized (listeners) {
				listeners.add(listener);
			}
			List<Data> data = new ArrayList<>(partitions.size());
			for (File p : partitions) data.add(FileData.get(p));
			listener.elementsReady(data);
		}
		return new FakeWorkProgress();
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		// TODO if from a drive, partition information
		return FileSystemDirectoryFormat.instance.getSubDataCommonProperties();
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO if from a drive, partition information
		return FileSystemDirectoryFormat.instance.getSubDataCommonProperties(subData);
	}

}
