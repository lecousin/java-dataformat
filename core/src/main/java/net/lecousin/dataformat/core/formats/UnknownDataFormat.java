package net.lecousin.dataformat.core.formats;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class UnknownDataFormat implements DataFormat {

	public static UnknownDataFormat instance = new UnknownDataFormat();
	
	private UnknownDataFormat() {}
	
	@Override
	public LocalizableString getName() {
		return new LocalizableString("dataformat", "Unknown");
	}
	
	@Override
	public AsyncWork<DataFormatInfo,Exception> getInfo(Data data, byte priority) {
		return null;
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/core/images/unknown_", ".png", 16, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	@Override
	public String[] getFileExtensions() {
		return EmptyDataFormat.nothing;
	}
	@Override
	public String[] getMIMETypes() {
		return EmptyDataFormat.nothing;
	}
}
