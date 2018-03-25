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
import net.lecousin.framework.progress.WorkProgress;

public class RemoveFilesAction extends RemoveDataAction<IOException> {

	public static final RemoveFilesAction instance = new RemoveFilesAction();
	
	private RemoveFilesAction() {}
	
	@Override
	public AsyncWork<List<Void>, IOException> execute(List<Data> data, Void parameter, byte priority, WorkProgress progress, long work) {
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
			File f = fd.file;
			Task.OnFile<?,IOException> task;
			if (fd.isDirectory) {
				long step = (work / total) * 10;
				work -= step;
				total -= 10;
				task = new RemoveDirectoryTask(f, progress, step, null, priority, false);
			} else {
				long step = work / total;
				work -= step;
				total--;
				task = new RemoveFileTask(f, priority);
				task.getOutput().listenInline(() -> { progress.progress(step); });
			}
			task.start();
			jp.addToJoin(task);
		}
		jp.start();
		AsyncWork<List<Void>, IOException> result = new AsyncWork<>();
		jp.listenInline(() -> { result.unblockSuccess(null); }, result);
		return result;
	}

}
