package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;

public interface InitNewDataAction<TParam, TError extends Exception> {

	public TParam createParameters();
	
	public IAsync<TError> execute(Data data, IO.Writable io, TParam params, WorkProgress progress, long work);
	
}
