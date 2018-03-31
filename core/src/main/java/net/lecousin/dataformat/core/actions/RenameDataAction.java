package net.lecousin.dataformat.core.actions;

import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class RenameDataAction<TParam extends RenameDataAction.Param, TError extends Exception> implements DataAction.SingleData<TParam, Void, TError> {

	public static interface Param {
		public String getName();
		public void setName(String name);
	}
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Rename");
	}
	
	@Override
	public IconProvider iconProvider() {
		return new IconProvider.FromPath("net/lecousin/dataformat/core/images/rename_", ".png", 16);
	}
	
}
