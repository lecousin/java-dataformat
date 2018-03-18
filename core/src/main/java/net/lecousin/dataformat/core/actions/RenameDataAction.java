package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class RenameDataAction<TError extends Exception> implements DataAction.SingleData<RenameDataAction.RenameParameter, Void, TError> {

	public static class RenameParameter {
		
		@LocalizedName(namespace="b", key="Name")
		public String name;
		
	}
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Rename");
	}
	
	@Override
	public IconProvider iconProvider() {
		return new IconProvider.FromPath("net/lecousin/dataformat/core/images/rename_", ".png", 16);
	}
	
	@Override
	public Type getType() {
		return Type.DATA_MODIFICATION;
	}
	
	@Override
	public RenameParameter createParameter(Data data) {
		RenameParameter p = new RenameParameter();
		p.name = data.getName();
		return p;
	}
	
}
