package net.lecousin.dataformat.core.actions;

import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.util.DataIcons;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class RemoveDataAction<TError extends Exception> implements DataAction<Void, Void, TError> {

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Remove");
	}
	
	@Override
	public IconProvider iconProvider() {
		return DataIcons.ICON_REMOVE_FILE;
	}
	
	@Override
	public Void createParameter(List<Data> data) {
		return null;
	}
	
}
