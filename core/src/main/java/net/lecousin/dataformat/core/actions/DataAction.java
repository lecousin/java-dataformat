package net.lecousin.dataformat.core.actions;

import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataAction<TParam, TResult, TError extends Exception> {
	
	public ILocalizableString getName();
	
	public IconProvider iconProvider();

	public TParam createParameter(List<Data> data);

	public AsyncSupplier<List<TResult>, TError> execute(List<Data> data, TParam parameter, Priority priority, WorkProgress progress, long work);
	
	public interface SingleData<TParam, TResult, TError extends Exception> extends DataAction<TParam, TResult, TError> {
		
		public TParam createParameter(Data data);

		public AsyncSupplier<TResult, TError> execute(Data data, TParam parameter, Priority priority, WorkProgress progress, long work);
		
		@Override
		public default TParam createParameter(List<Data> data) {
			return createParameter(data.get(0));
		}

		@Override
		public default AsyncSupplier<List<TResult>, TError> execute(List<Data> data, TParam parameter, Priority priority, WorkProgress progress, long work) {
			AsyncSupplier<List<TResult>, TError> result = new AsyncSupplier<>();
			execute(data.get(0), parameter, priority, progress, work).onDone(
				(res) -> { result.unblockSuccess(Collections.singletonList(res)); },
				result
			);
			return result;
		}
		
	}
	
}
