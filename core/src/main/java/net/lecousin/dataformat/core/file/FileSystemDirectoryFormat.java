package net.lecousin.dataformat.core.file;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class FileSystemDirectoryFormat implements DataFormat.DataContainerFlat {

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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		// TODO Auto-generated method stub
		
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
