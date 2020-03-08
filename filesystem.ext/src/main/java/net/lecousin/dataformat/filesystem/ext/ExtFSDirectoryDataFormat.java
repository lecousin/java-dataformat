package net.lecousin.dataformat.filesystem.ext;

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

public class ExtFSDirectoryDataFormat implements ContainerDataFormat.ContainerDirectory {

	public static final ExtFSDirectoryDataFormat instance = new ExtFSDirectoryDataFormat();
	
	private ExtFSDirectoryDataFormat() {}

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
		return ExtFSDataFormat.instance.listenDirectorySubData((ExtFSData)container, listener);
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return ExtFSDataFormat.instance.getSubDataCommonProperties();
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return ExtFSDataFormat.instance.getSubDataCommonProperties(subData);
	}
	
}
