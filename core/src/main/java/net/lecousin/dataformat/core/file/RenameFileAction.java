package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.RenameDataAction;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class RenameFileAction extends RenameDataAction<RenameFileAction.RenameParameter, IOException> {

	public static final RenameFileAction instance = new RenameFileAction();
	
	private RenameFileAction() {}
	
	public static class RenameParameter implements RenameDataAction.Param {
		
		// TODO validation
		@LocalizedName(namespace="b", key="Name")
		public String name;
		
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
	public RenameParameter createParameter(Data data) {
		RenameParameter p = new RenameParameter();
		p.name = data.getName().appLocalizationSync();
		return p;
	}
	
	@Override
	public AsyncSupplier<Void, IOException> execute(Data data, RenameParameter parameter, Priority priority, WorkProgress progress, long work) {
		FileData fd = (FileData)data;
		FileData parent = FileData.get(fd.file.getParentFile());
		Task<Void, IOException> task = Task.file(parent.file, "Rename file", priority, t -> {
			File dest = new File(parent.file, parameter.name);
			if (dest.exists())
				throw new FileAlreadyExistsException(dest.getAbsolutePath());
			if (!fd.file.renameTo(dest))
				throw new IOException("Unable to rename file " + fd.file.getAbsolutePath() + " to " + parameter.name);
			progress.progress(work);
			return null;
		});
		task.start();
		return task.getOutput();
	}

}
