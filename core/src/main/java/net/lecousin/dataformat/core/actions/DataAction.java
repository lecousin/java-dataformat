package net.lecousin.dataformat.core.actions;

import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataAction {
	
	public enum Type {
		DATA_MODIFICATION, // such as rename, remove...
		CONTENT_MODIFICATION, // such as conversion...
		DATA_CREATION, // such as new folder, new data...
	}

	public ILocalizableString getName();
	
	public IconProvider iconProvider();
	
	public Type getType();
	
	public interface SingleData<TParam, TResult, TError extends Exception> extends DataAction {
		
		public AsyncWork<Boolean, NoException> canExecute(Data data);
		
		public TParam createParameter(Data data);
		
		public AsyncWork<TResult, TError> execute(Data data, TParam parameter, byte priority);
		
	}
	
	public interface MultipleData<TParam, TResult, TError extends Exception> extends SingleData<TParam, TResult, TError> {
		
		public AsyncWork<Boolean, NoException> canExecute(List<Data> data);
		
		public TParam createParameter(List<Data> data);
		
		public AsyncWork<List<TResult>, TError> execute(List<Data> data, TParam parameter, byte priority);

		@Override
		public default AsyncWork<Boolean, NoException> canExecute(Data data) {
			return canExecute(Collections.singletonList(data));
		}
		
		@Override
		public default TParam createParameter(Data data) {
			return createParameter(Collections.singletonList(data));
		}
		
		@Override
		public default AsyncWork<TResult, TError> execute(Data data, TParam parameter, byte priority) {
			AsyncWork<TResult, TError> result = new AsyncWork<>();
			AsyncWork<List<TResult>, TError> exe = execute(Collections.singletonList(data), parameter, priority);
			exe.listenInline((res) -> {
				if (res != null && !res.isEmpty())
					result.unblockSuccess(res.get(0));
				else
					result.unblockSuccess(null);
			}, result);
			return result;
		}
		
	}
	
}
