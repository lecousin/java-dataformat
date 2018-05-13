package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.ui.iconset.files.FilesIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class CreateContainerDataAction<TParam extends CreateContainerDataAction.Param, TError extends Exception> implements DataAction.SingleData<TParam, Data, TError> {

	public static interface Param {
		public String getName();
		public void setName(String name);
	}
	
	@Override
	public IconProvider iconProvider() {
		return FilesIconSet.Icons.NEW_FOLDER.get();
	}
	
}
