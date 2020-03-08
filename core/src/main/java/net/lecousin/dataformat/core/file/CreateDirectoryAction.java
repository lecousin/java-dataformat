package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.CreateContainerDataAction;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class CreateDirectoryAction extends CreateContainerDataAction<CreateDirectoryAction.NewDataParameter, IOException> {

	public static final CreateDirectoryAction instance = new CreateDirectoryAction();
	
	private CreateDirectoryAction() {}
	
	public static class NewDataParameter implements CreateContainerDataAction.Param {
		
		@LocalizedName(namespace="b", key="Name")
		public String name = "";

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public void setName(String name) {
			this.name = name;
		}
	}
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "New directory");
	}
	
	@Override
	public NewDataParameter createParameter(Data data) {
		NewDataParameter p = new NewDataParameter();
		return p;
	}

	@SuppressWarnings("resource")
	@Override
	public AsyncSupplier<Data, IOException> execute(Data data, NewDataParameter parameter, Priority priority, WorkProgress progress, long work) {
		File file = new File(((FileData)data).file, parameter.name);
		if (file.exists())
			return new AsyncSupplier<>(null, new FileAlreadyExistsException(file.getAbsolutePath()));
		try {
			if (!file.mkdir())
				throw new IOException("Unable to create directory " + file.getAbsolutePath());
		} catch (IOException err) {
			return new AsyncSupplier<>(null, err);
		}
		progress.progress(work);
		return new AsyncSupplier<>(FileData.get(file), null);
	}

}
