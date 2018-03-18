package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public abstract class CreateDataAction<TError extends Exception> implements DataAction.SingleData<CreateDataAction.NewDataParameter, Pair<Data, IO.Writable>, TError> {

	public static class NewDataParameter {
		
		@LocalizedName(namespace="b", key="Name")
		public String name = "";

	}

	@Override
	public Type getType() {
		return Type.DATA_CREATION;
	}

	@Override
	public IconProvider iconProvider() {
		return new IconProvider.FromPath("net/lecousin/dataformat/core/images/file_add_", ".png", 16, 32, 48, 64, 128);
	}
	
	@Override
	public NewDataParameter createParameter(Data data) {
		return new NewDataParameter();
	}
	
}
