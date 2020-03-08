package net.lecousin.dataformat.core;

import java.util.List;

import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.actions.InitNewDataAction;
import net.lecousin.dataformat.model.ModelBlock;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataFormat {
	
	public ILocalizableString getName();
	
	public IconProvider getIconProvider();
	
	public String[] getFileExtensions();
	public String[] getMIMETypes();
	
	public AsyncSupplier<? extends DataFormatInfo,?> getInfo(Data data, Priority priority);
	
	public default AsyncSupplier<ModelBlock, Exception> getModel(@SuppressWarnings("unused") Data data) {
		return null;
	}
	
	public default List<DataAction<?, ?, ?>> getActions(@SuppressWarnings("unused") Data data) {
		return null;
	}
	
	public default InitNewDataAction<?, ?> getInitNewDataAction() {
		return null;
	}
	
}
