package net.lecousin.dataformat.core.formats;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class EmptyDataFormat implements DataFormat {

	public static EmptyDataFormat instance = new EmptyDataFormat();
	
	private EmptyDataFormat() {}
	
	@Override
	public LocalizableString getName() {
		return new LocalizableString("dataformat", "Empty");
	}
	
	@Override
	public AsyncWork<DataFormatInfo,Exception> getInfo(Data data, byte priority) {
		return null;
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/core/images/blank_", ".png", 16, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }

	public static final String[] nothing = new String[0];
	@Override
	public String[] getFileExtensions() {
		return nothing;
	}
	@Override
	public String[] getMIMETypes() {
		return nothing;
	}
	
}
