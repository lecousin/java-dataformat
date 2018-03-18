package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.RemoveDataAction;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.concurrent.tasks.drives.RemoveDirectoryTask;
import net.lecousin.framework.concurrent.tasks.drives.RemoveFileTask;
import net.lecousin.framework.exception.NoException;

public class RemoveFilesAction extends RemoveDataAction<IOException> {

	@Override
	public AsyncWork<Boolean, NoException> canExecute(List<Data> data) {
		boolean files = true;
		for (Data d : data)
			if (!(d instanceof FileData)) {
				files = false;
				break;
			}
		return new AsyncWork<>(Boolean.valueOf(files), null);
	}

	@Override
	public AsyncWork<List<Void>, IOException> execute(List<Data> data, Void parameter, byte priority) {
		// TODO progress
		JoinPoint<IOException> jp = new JoinPoint<>();
		for (Data d : data) {
			FileData fd = (FileData)d;
			File f = fd.file;
			Task.OnFile<?,IOException> task;
			if (f.isDirectory())
				task = new RemoveDirectoryTask(f, null, 0, null, priority, false);
			else
				task = new RemoveFileTask(f, priority);
			task.start();
			jp.addToJoin(task);
		}
		jp.start();
		AsyncWork<List<Void>, IOException> result = new AsyncWork<>();
		jp.listenInline(() -> { result.unblockSuccess(null); }, result);
		return result;
	}

}
