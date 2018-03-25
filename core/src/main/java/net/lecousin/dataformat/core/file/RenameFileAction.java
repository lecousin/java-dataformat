package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.RenameDataAction;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class RenameFileAction extends RenameDataAction<RenameFileAction.RenameParameter, IOException> {

	public static final RenameFileAction instance = new RenameFileAction();
	
	private RenameFileAction() {}
	
	public static class RenameParameter {
		
		// TODO validation
		@LocalizedName(namespace="b", key="Name")
		public String name;
		
	}
	
	@Override
	public RenameParameter createParameter(Data data) {
		RenameParameter p = new RenameParameter();
		p.name = data.getName();
		return p;
	}
	
	@Override
	public AsyncWork<Void, IOException> execute(Data data, RenameParameter parameter, byte priority, WorkProgress progress, long work) {
		FileData fd = (FileData)data;
		FileData parent = FileData.get(fd.file.getParentFile());
		Task.OnFile<Void, IOException> task = new Task.OnFile<Void, IOException>(parent.file, "Rename file", priority) {
			@Override
			public Void run() throws IOException {
				File dest = new File(parent.file, parameter.name);
				if (dest.exists())
					throw new FileAlreadyExistsException(dest.getAbsolutePath());
				if (!fd.file.renameTo(dest))
					throw new IOException("Unable to rename file " + fd.file.getAbsolutePath() + " to " + parameter.name);
				progress.progress(work);
				return null;
			}
		};
		task.start();
		return task.getOutput();
	}

}
