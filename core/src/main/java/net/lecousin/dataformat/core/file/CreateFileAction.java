package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.CreateDataAction;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.util.Pair;

public class CreateFileAction extends CreateDataAction<CreateFileAction.NewDataParameter, IOException> {

	public static final CreateFileAction instance = new CreateFileAction();
	
	private CreateFileAction() {}
	
	public static class NewDataParameter implements CreateDataAction.Param {
		
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
		return new LocalizableString("dataformat", "New file");
	}
	
	@Override
	public NewDataParameter createParameter(Data data) {
		NewDataParameter p = new NewDataParameter();
		return p;
	}

	@SuppressWarnings("resource")
	@Override
	public AsyncWork<Pair<Data, IO.Writable>, IOException> execute(Data data, NewDataParameter parameter, byte priority, WorkProgress progress, long work) {
		File file = new File(((FileData)data).file, parameter.name);
		if (file.exists())
			return new AsyncWork<>(null, new FileAlreadyExistsException(file.getAbsolutePath()));
		try {
			if (!file.createNewFile())
				throw new IOException("Unable to create file " + file.getAbsolutePath());
		} catch (IOException err) {
			return new AsyncWork<>(null, err);
		}
		FileIO.WriteOnly io = new FileIO.WriteOnly(file, priority);
		progress.progress(work);
		return new AsyncWork<>(new Pair<>(FileData.get(file), io), null);
	}

}
