package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.CreateDataAction;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.util.Pair;

public class CreateFileAction extends CreateDataAction<IOException> {

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "New file");
	}

	@Override
	public AsyncWork<Boolean, NoException> canExecute(Data data) {
		return new AsyncWork<>(Boolean.valueOf((data instanceof FileData) && ((FileData)data).isDirectory), null);
	}

	@SuppressWarnings("resource")
	@Override
	public AsyncWork<Pair<Data, IO.Writable>, IOException> execute(Data data, NewDataParameter parameter, byte priority) {
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
		return new AsyncWork<>(new Pair<>(FileData.get(file), io), null);
	}

}
