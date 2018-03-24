package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataFormat {
	
	public ILocalizableString getName();
	
	public IconProvider getIconProvider();
	
	public String[] getFileExtensions();
	public String[] getMIMETypes();
	
	public AsyncWork<? extends DataFormatInfo,?> getInfo(Data data, byte priority);
	
}
