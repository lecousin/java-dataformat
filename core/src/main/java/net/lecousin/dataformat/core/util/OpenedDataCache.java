package net.lecousin.dataformat.core.util;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.event.Listener;
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
	
	protected abstract AsyncWork<T,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work);
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

	public AsyncWork<CachedObject<T>, Exception> open(Data data, Object user, byte priority, WorkProgress progress, long work) {
		synchronized (data) {
			@SuppressWarnings("unchecked")
			Cached cached = (Cached)data.useCachedData(type.getName(), user);
			if (cached != null) {
				if (progress != null) progress.progress(work);
				return new AsyncWork<>(cached, null);
			}
			if (data.hasProperty(type.getName() + "Loading")) {
				Integer count = (Integer)data.getProperty(type.getName() + "LoadingCounter");
				data.setProperty(type.getName() + "LoadingCounter", new Integer(count.intValue()+1));
				AsyncWork<CachedObject<T>,Exception> sp = new AsyncWork<>();
				@SuppressWarnings("unchecked")
				AsyncWork<CachedObject<T>,Exception> loading = (AsyncWork<CachedObject<T>,Exception>)data.getProperty(type.getName() + "Loading");
				loading.listenInline(new AsyncWorkListener<CachedObject<T>, Exception>() {
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
				sp.onCancel(new Listener<CancelException>() {
					@Override
					public void fire(CancelException event) {
						synchronized (data) {
							Integer count = (Integer)data.getProperty(type.getName() + "LoadingCounter");
							if (count.intValue() == 1) {
								loading.unblockCancel(event);
								data.removeProperty(type.getName() + "LoadingCounter");
								data.removeProperty(type.getName() + "Loading");
							} else
								data.setProperty(type.getName() + "LoadingCounter", new Integer(count.intValue()-1));
						}
					}
				});
				return sp;
			}
			AsyncWork<CachedObject<T>,Exception> loading = new AsyncWork<>();
			AsyncWork<CachedObject<T>,Exception> sp = new AsyncWork<>();
			data.setProperty(type.getName() + "Loading", loading);
			data.setProperty(type.getName() + "LoadingCounter", new Integer(1));
			AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.openReadOnly(priority);
			Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Loading " + type.getSimpleName(), priority) {
				@SuppressWarnings("resource")
				@Override
				public Void run() {
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
						Cached cached = new Cached(null);
						synchronized (data) {
							data.setCachedData(type.getName(), cached);
							data.removeProperty(type.getName() + "Loading");
							data.removeProperty(type.getName() + "LoadingCounter");
						}
						loading.unblockError(new Exception("Cannot open data for reading"));
						return null;
					}
					AsyncWork<T,Exception> open = open(data, io, progress, work-work/4);
					Runnable onOpen = new Runnable() {
						@Override
						public void run() {
							if (!open.isSuccessful()) {
								synchronized (data) {
									data.removeProperty(type.getName() + "Loading");
									data.removeProperty(type.getName() + "LoadingCounter");
								}
								if (open.hasError())
									loading.unblockError(open.getError());
								else
									loading.cancel(open.getCancelEvent());
							} else {
								Cached cached = new Cached(open.getResult());
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
					if (open.isUnblocked()) onOpen.run();
					else open.listenAsync(new Task.Cpu<Void, NoException>("Cache opened data", priority) {
						@Override
						public Void run() {
							onOpen.run();
							return null;
						}
					}, true);
					return null;
				}
			};
			task.startOn(open, true);
			sp.onCancel(new Listener<CancelException>() {
				@Override
				public void fire(CancelException event) {
					synchronized (data) {
						Integer count = (Integer)data.getProperty(type.getName() + "LoadingCounter");
						if (count.intValue() == 1) {
							loading.unblockCancel(event);
							data.removeProperty(type.getName() + "LoadingCounter");
							data.removeProperty(type.getName() + "Loading");
						} else
							data.setProperty(type.getName() + "LoadingCounter", new Integer(count.intValue()-1));
					}
				}
			});
			loading.listenInline(new AsyncWorkListener<CachedObject<T>, Exception>() {
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
