package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.actions.RemoveDataAction;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.JoinPoint;
import net.lecousin.framework.concurrent.tasks.drives.RemoveDirectory;
import net.lecousin.framework.concurrent.tasks.drives.RemoveFile;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.progress.WorkProgress;

public class RemoveFilesAction extends RemoveDataAction<IOException> {

	public static final RemoveFilesAction instance = new RemoveFilesAction();
	
	private RemoveFilesAction() {}
	
	@Override
	public AsyncSupplier<List<Void>, IOException> execute(List<Data> data, Void parameter, Priority priority, WorkProgress progress, long work) {
		JoinPoint<IOException> jp = new JoinPoint<>();
		int nbFiles = 0;
		int nbDirs = 0;
		for (Data d : data)
			if (((FileData)d).isDirectory)
				nbDirs++;
			else
				nbFiles++;
		int total = nbFiles + nbDirs * 10;
		for (Data d : data) {
			FileData fd = (FileData)d;
			fd.forceCloseIOReadOnly();
			File f = fd.file;
			Task<?,IOException> task;
			if (fd.isDirectory) {
				long step = (work / total) * 10;
				work -= step;
				total -= 10;
				task = RemoveDirectory.task(f, progress, step, null, priority, false);
			} else {
				long step = work / total;
				work -= step;
				total--;
				task = RemoveFile.task(f, priority);
				task.getOutput().onDone(() -> { progress.progress(step); });
			}
			task.start();
			jp.addToJoin(task);
		}
		jp.start();
		AsyncSupplier<List<Void>, IOException> result = new AsyncSupplier<>();
		jp.onDone(() -> { result.unblockSuccess(null); }, result);
		return result;
	}

}
