package net.lecousin.dataformat.core.file;

import java.io.File;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.actions.CreateDataAction;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class FileSystemDirectoryFormat implements ContainerDataFormat.ContainerDirectory {

	public static final FileSystemDirectoryFormat instance = new FileSystemDirectoryFormat();
	
	private FileSystemDirectoryFormat() {}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Directory");
	}

	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		return new AsyncWork<>(null, null);
	}

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/core/images/folder_", ".png", 16, 24, 32, 48, 64, 256);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }

	@Override
	public String[] getFileExtensions() {
		return new String[0];
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}
	
	@Override
	public CreateDataAction<?, ?> getCreateNewDataAction() {
		return CreateFileAction.instance;
	}
	
	@Override
	public List<DataAction<?, ?, ?>> getSubDataActions(List<Data> data) {
		List<DataAction<?, ?, ?>> list = new LinkedList<>();
		if (data.size() == 1) {
			list.add(RenameFileAction.instance);
		}
		list.add(RemoveFilesAction.instance);
		return list;
	}

	private static final ContainerDataFormat.CacheSubData cacheSubData = new ContainerDataFormat.CacheSubData() {
		@Override
		public long getCacheTimeout() {
			return 3 * 60 * 1000; // 3 minutes
		}
		@Override
		public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
			FileData dir = (FileData)container;
			WorkProgress progress = new WorkProgressImpl(1000, new LocalizableString("dataformat", "Listing files").appLocalizationSync());
			new Task.OnFile<Void, NoException>(dir.file, "Read directory content", Task.PRIORITY_NORMAL) {
				@Override
				public Void run() {
					File[] files = dir.file.listFiles();
					if (files == null) {
						Exception error = new AccessDeniedException(dir.file.getAbsolutePath());
						listener.error(error);
						progress.error(error);
						return null;
					}
					progress.progress(800);
					new Task.Cpu.FromRunnable("List directory content as data", Task.PRIORITY_NORMAL, () -> {
						ArrayList<Data> list = new ArrayList<>(files.length);
						for (File f : files)
							list.add(FileData.get(f));
						listener.elementsReady(list);
						progress.done();
					}).start();
					return null;
				}
			}.start();
			// TODO start file system watcher
			return progress;
		}
		@Override
		public void unlistenSubData(Data container, CollectionListener<Data> listener) {
			// TODO stop file system watcher
		}
	};
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		return ContainerDataFormat.CacheSubData.listenSubData(cacheSubData, container, listener);
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
		ContainerDataFormat.CacheSubData.unlistenSubData(cacheSubData, container, listener);
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

}
