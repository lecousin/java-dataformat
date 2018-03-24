package net.lecousin.dataformat.core.hierarchy;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class DirectoryDataFormat  implements ContainerDataFormat {

	public DirectoryDataFormat(ContainerDataFormat containerFormat) {
		this.containerFormat = containerFormat;
	}
	
	protected ContainerDataFormat containerFormat;

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
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		return containerFormat.listenSubData(container, listener);
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
		containerFormat.unlistenSubData(container, listener);
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return containerFormat.getSubDataCommonProperties();
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return containerFormat.getSubDataCommonProperties(subData);
	}

}
