package net.lecousin.dataformat.core.util;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;

public abstract class OpenedDataCache<T> {
	
	public OpenedDataCache(Class<T> type, long cacheExpiration) {
		this.type = type;
		this.cacheExpiration = cacheExpiration;
	}
	
	protected Class<T> type;
	protected long cacheExpiration;
	
	protected abstract AsyncSupplier<T, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work);
	protected abstract boolean closeIOafterOpen();
	protected abstract void close(T object);
	
	protected class Cached extends CachedObject<T> {
		private Cached(T object) {
			super(object, cacheExpiration);
		}
		@Override
		protected void closeCachedObject(T object) {
			OpenedDataCache.this.close(object);
		}
	}

	public AsyncSupplier<CachedObject<T>, Exception> open(Data data, Object user, Priority priority, WorkProgress progress, long work) {
		synchronized (data) {
			@SuppressWarnings("unchecked")
			Cached cached = (Cached)data.useCachedData(type.getName(), user);
			if (cached != null) {
				if (progress != null) progress.progress(work);
				return new AsyncSupplier<>(cached, null);
			}
			if (data.hasProperty(type.getName() + "Loading")) {
				Integer count = (Integer)data.getProperty(type.getName() + "LoadingCounter");
				data.setProperty(type.getName() + "LoadingCounter", new Integer(count.intValue()+1));
				AsyncSupplier<CachedObject<T>,Exception> sp = new AsyncSupplier<>();
				@SuppressWarnings("unchecked")
				AsyncSupplier<CachedObject<T>,Exception> loading = (AsyncSupplier<CachedObject<T>,Exception>)data.getProperty(type.getName() + "Loading");
				loading.listen(new Listener<CachedObject<T>, Exception>() {
					@Override
					public void ready(CachedObject<T> result) {
						result.use(user);
						if (progress != null) progress.progress(work);
						sp.unblockSuccess(result);
					}
					@Override
					public void error(Exception error) {
						sp.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
					}
				});
				sp.onCancel(event -> {
					synchronized (data) {
						Integer count2 = (Integer)data.getProperty(type.getName() + "LoadingCounter");
						if (count2.intValue() == 1) {
							loading.unblockCancel(event);
							data.removeProperty(type.getName() + "LoadingCounter");
							data.removeProperty(type.getName() + "Loading");
						} else
							data.setProperty(type.getName() + "LoadingCounter", new Integer(count2.intValue()-1));
					}
				});
				return sp;
			}
			AsyncSupplier<CachedObject<T>,Exception> loading = new AsyncSupplier<>();
			AsyncSupplier<CachedObject<T>,Exception> sp = new AsyncSupplier<>();
			data.setProperty(type.getName() + "Loading", loading);
			data.setProperty(type.getName() + "LoadingCounter", new Integer(1));
			AsyncSupplier<? extends IO.Readable.Seekable, IOException> open = data.openReadOnly(priority);
			Task<Void,NoException> task = Task.cpu("Loading " + type.getSimpleName(), priority, t -> {
				if (progress != null) progress.progress(work/4);
				if (open.isCancelled()) return null;
				if (!open.isSuccessful()) {
					synchronized (data) {
						data.removeProperty(type.getName() + "Loading");
						data.removeProperty(type.getName() + "LoadingCounter");
					}
					loading.unblockError(open.getError());
					return null;
				}
				IO.Readable io = open.getResult();
				if (io == null) {
					Cached c = new Cached(null);
					synchronized (data) {
						data.setCachedData(type.getName(), c);
						data.removeProperty(type.getName() + "Loading");
						data.removeProperty(type.getName() + "LoadingCounter");
					}
					loading.unblockError(new Exception("Cannot open data for reading"));
					return null;
				}
				AsyncSupplier<T, ? extends Exception> open2 = open(data, io, progress, work-work/4);
				Runnable onOpen = new Runnable() {
					@Override
					public void run() {
						if (!open2.isSuccessful()) {
							synchronized (data) {
								data.removeProperty(type.getName() + "Loading");
								data.removeProperty(type.getName() + "LoadingCounter");
							}
							if (open2.hasError())
								loading.unblockError(open2.getError());
							else
								loading.cancel(open2.getCancelEvent());
						} else {
							Cached cached = new Cached(open2.getResult());
							cached.use(user);
							synchronized (data) {
								data.setCachedData(type.getName(), cached);
								data.removeProperty(type.getName() + "Loading");
								data.removeProperty(type.getName() + "LoadingCounter");
							}
							loading.unblockSuccess(cached);
						}
						if (closeIOafterOpen())
							io.closeAsync();
					}
				};
				if (open2.isDone()) onOpen.run();
				else open2.thenStart("Cache opened data", priority, (Task<Void, NoException> t2) -> {
					onOpen.run();
					return null;
				}, true);
				return null;
			});
			task.startOn(open, true);
			sp.onCancel(event -> {
				synchronized (data) {
					Integer count = (Integer)data.getProperty(type.getName() + "LoadingCounter");
					if (count.intValue() == 1) {
						loading.unblockCancel(event);
						data.removeProperty(type.getName() + "LoadingCounter");
						data.removeProperty(type.getName() + "Loading");
					} else
						data.setProperty(type.getName() + "LoadingCounter", new Integer(count.intValue()-1));
				}
			});
			loading.listen(new Listener<CachedObject<T>, Exception>() {
				@Override
				public void ready(CachedObject<T> result) {
					sp.unblockSuccess(result);
				}
				@Override
				public void error(Exception error) {
					sp.unblockError(error);
				}
				@Override
				public void cancelled(CancelException event) {
					task.cancel(event);
					open.unblockCancel(event);
				}
			});
			return sp;
		}
	}
	
}
