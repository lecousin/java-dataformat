package net.lecousin.dataformat.filesystem.fat;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui.iconset.files.FilesIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class FATDirectoryDataFormat implements ContainerDataFormat.ContainerDirectory {

	public static final FATDirectoryDataFormat instance = new FATDirectoryDataFormat();
	
	private FATDirectoryDataFormat() {}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Directory");
	}

	@Override
	public IconProvider getIconProvider() {
		return FilesIconSet.Icons.FOLDER.get();
	}

	@Override
	public String[] getFileExtensions() {
		return new String[0];
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}

	@Override
	public AsyncSupplier<? extends DataFormatInfo, ?> getInfo(Data data, Priority priority) {
		// TODO
		return new AsyncSupplier<>(null, null);
	}

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		return ((FatEntryData)container).listDirectoryContent(listener);
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		// TODO
		return null;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO
		return null;
	}
	
}
