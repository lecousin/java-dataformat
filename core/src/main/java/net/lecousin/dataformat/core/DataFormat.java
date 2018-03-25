package net.lecousin.dataformat.core;

import java.util.List;

import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataFormat {
	
	public ILocalizableString getName();
	
	public IconProvider getIconProvider();
	
	public String[] getFileExtensions();
	public String[] getMIMETypes();
	
	public AsyncWork<? extends DataFormatInfo,?> getInfo(Data data, byte priority);
	
	public default List<DataAction<?, ?, ?>> getActions(@SuppressWarnings("unused") Data data) {
		return null;
	}
	
	public default DataAction<?, ?, ?> getInitNewDataAction() {
		return null;
	}
	
}
