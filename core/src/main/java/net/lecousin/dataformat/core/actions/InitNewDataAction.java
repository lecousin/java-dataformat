package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;

public interface InitNewDataAction<TParam, TError extends Exception> {

	public TParam createParameters();
	
	public ISynchronizationPoint<TError> execute(Data data, IO.Writable io, TParam params, WorkProgress progress, long work);
	
}
