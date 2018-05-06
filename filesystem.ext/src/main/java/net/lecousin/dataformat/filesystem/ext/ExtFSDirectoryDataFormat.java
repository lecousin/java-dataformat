package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.util.DataIcons;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class ExtFSDirectoryDataFormat implements ContainerDataFormat.ContainerDirectory {

	public static final ExtFSDirectoryDataFormat instance = new ExtFSDirectoryDataFormat();
	
	private ExtFSDirectoryDataFormat() {}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Directory");
	}

	@Override
	public IconProvider getIconProvider() { return DataIcons.ICON_FOLDER; }

	@Override
	public String[] getFileExtensions() {
		return new String[0];
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}

	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		// TODO
		return new AsyncWork<>(null, null);
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
