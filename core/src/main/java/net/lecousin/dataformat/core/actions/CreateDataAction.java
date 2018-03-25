package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public abstract class CreateDataAction<TParam extends CreateDataAction.Param, TError extends Exception> implements DataAction.SingleData<TParam, Pair<Data, IO.Writable>, TError> {

	public static interface Param {
		public String getName();
		public void setName(String name);
	}
	
	@Override
	public IconProvider iconProvider() {
		return new IconProvider.FromPath("net/lecousin/dataformat/core/images/file_add_", ".png", 16, 32, 48, 64, 128);
	}
	
}
