package net.lecousin.dataformat.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.MutualExclusion;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.event.SingleEvent;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.util.Pair;

public abstract class Data {

	public static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(Data.class);
	}
	
	public abstract ILocalizableString getName();
	public abstract ILocalizableString getDescription();
	/** returns -1 if size is unknown. */
	public abstract long getSize();
	
	/** returns true if it has data inside, false if this is just a container but no data to analyze such as a file system directory. */
	public abstract boolean hasContent();
	/** return the parent data, containing this one. */
	public abstract Data getContainer();
	
	/* *************************************************************************** */
	/* IO Management:
	 * The data may be accessed concurrently, with different requirements.
	 * The source may be very limited (like compressed stream is only readable, not seekable...).
	 * The goal here is to provide most convenient way of accessing the data content
	 */
	
	private AsyncWork<Void, Exception> ioLoading = null;
	private IO.Readable io = null;
	private int ioUsage = 0;
	private Task<Void,NoException> closeIO = null;
	private AsyncWork<? extends IO, ? extends Exception> rwIO = null;
	private MutualExclusion<NoException> ioMutex = new MutualExclusion<>();
	
	public void forceCloseIOReadOnly() {
		ioMutex.lock();
		try {
			if (closeIO != null) {
				closeIO.cancel(new CancelException("forceCloseIOReadOnly called"));
				closeIO = null;
			}
			if (ioLoading != null) {
				ioLoading.cancel(new CancelException("forceCloseIOReadOnly called"));
				ioLoading = null;
			}
			if (io != null) {
				try { io.close(); } catch (Throwable t) {}
				io = null;
			}
		} finally {
			ioMutex.unlock();
		}
	}
	
	private AsyncWork<Void, Exception> prepareIOReadOnly(byte priority) {
		ioMutex.lock();
		try {
			if (closeIO != null) {
				closeIO.cancel(new CancelException("IO used again"));
				closeIO = null;
			}
			if (io != null) {
				if (io.getPriority() > priority)
					io.setPriority(priority);
				ioUsage++;
				return new AsyncWork<>(null, null);
			}
			if (ioLoading == null) {
				// first usage
				AsyncWork<IO.Readable,? extends Exception> originalIO = openIOReadOnly(priority);
				if (originalIO == null)
					return new AsyncWork<>(null,null);
				ioLoading = new AsyncWork<Void, Exception>();
				ioLoading.onCancel((cancel) -> { originalIO.unblockCancel(cancel); });
				originalIO.listenAsync(new Task.Cpu.FromRunnable("Open data IO", priority, () -> {
					ioMutex.lock();
					try {
						if (originalIO.isCancelled() || ioLoading == null)
							return;
						if (!originalIO.isSuccessful()) {
							ioLoading.unblockError(originalIO.getError());
							return;
						}
						IO.Readable oio = originalIO.getResult();
						if (oio == null) {
							ioLoading.unblockSuccess(null);
							return;
						}
						if (oio instanceof IO.Readable.Seekable) {
							// this is a good one
							if (oio instanceof IO.Readable.Buffered) {
								// this is a perfect one
								io = oio;
							} else {
								// it is not buffered, let's add this feature
								io = new BufferedIO((IO.Readable.Seekable)oio, getSize(), 512, 16384, false);
							}
						} else {
							// only streaming, let's make it better
							try { io = new ReadableToSeekable(oio, 16384); }
							catch (IOException e) {
								ioLoading.unblockError(e);
								return;
							}
						}
						ioLoading.unblockSuccess(null);
					} finally {
						ioMutex.unlock();
					}
				}), true);
			}
			ioUsage++;
			AsyncWork<Void,Exception> sp = new AsyncWork<>();
			sp.onCancel((event) -> {
				// caller requested to cancel
				ioMutex.lock();
				try {
					if (ioUsage == 1) {
						if (ioLoading != null) {
							ioLoading.unblockCancel(event);
							if (!ioLoading.isCancelled())
								releaseIOReadOnly();
							else {
								ioLoading = null;
								ioUsage = 0;
							}
						}
					} else
						ioUsage--;
				} finally {
					ioMutex.unlock();
				}
			});
			ioLoading.listenInline(new AsyncWorkListener<Void, Exception>() {
				@Override
				public void ready(Void result) {
					sp.unblockSuccess(null);
					ioLoading = null;
				}
				@Override
				public void error(Exception error) {
					sp.unblockError(error);
					ioLoading = null;
				}
				@Override
				public void cancelled(CancelException event) {
				}
			});
			return sp;
		} finally {
			ioMutex.unlock();
		}
	}
	
	private void releaseIOReadOnly() {
		ioMutex.lock();
		try {
			ioUsage--;
			if (ioUsage == 0) {
				ioLoading = null;
				closeIO = new Task.Cpu<Void,NoException>("Closing data IO", Task.PRIORITY_RATHER_LOW) {
					@Override
					public Void run() {
						ioMutex.lock();
						try {
							if (!isCancelled() && ioUsage == 0) {
								try { io.close(); } catch (Throwable t) { /* ignore */ }
								io = null;
							}
							closeIO = null;
						} finally {
							ioMutex.unlock();
						}
						return null;
					}
				};
				closeIO.executeIn(5000);
				closeIO.start();
			}
		} finally {
			ioMutex.unlock();
		}
	}
	
	public <T extends IO.Readable.Seekable&IO.Readable.Buffered&IO.KnownSize> AsyncWork<T,Exception> openReadOnly(byte priority) {
		if (rwIO != null)
			return new AsyncWork<>(null, new Exception("Data already open for modification"));
		AsyncWork<Void, Exception> prepare = prepareIOReadOnly(priority);
		AsyncWork<T,Exception> sp = new AsyncWork<>();
		prepare.listenInline(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				if (prepare.isCancelled())
					return;
				if (!prepare.isSuccessful()) {
					sp.unblockError(prepare.getError());
					return;
				}
				if (io == null) {
					sp.unblockSuccess(null);
					return;
				}
				// wrap the original IO inside a SubIO so every operation will be done using an absolute position
				// allowing concurrent access to the IO
				long size = getSize();
				if (size >= 0) {
					@SuppressWarnings("resource")
					SubIO.Readable.Seekable.Buffered wrap = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)io, 0, size, io.getSourceDescription(), false);
					wrap.addCloseListener(new Runnable() {
						@Override
						public void run() {
							releaseIOReadOnly();
						}
					});
					sp.unblockSuccess((T)wrap);
				} else {
					AsyncWork<Long, IOException> getSize = ((IO.KnownSize)io).getSizeAsync();
					getSize.listenAsync(new Task.Cpu.FromRunnable("Open Data IO", priority, () -> {
						if (!getSize.isSuccessful()) {
							releaseIOReadOnly();
							if (getSize.hasError())
								sp.error(getSize.getError());
							else
								sp.cancel(getSize.getCancelEvent());
							return;
						}
						@SuppressWarnings("resource")
						SubIO.Readable.Seekable.Buffered wrap = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)io, 0, getSize.getResult().longValue(), io.getSourceDescription(), false);
						wrap.addCloseListener(new Runnable() {
							@Override
							public void run() {
								releaseIOReadOnly();
							}
						});
						sp.unblockSuccess((T)wrap);
					}), true);
				}
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				prepare.unblockCancel(event);
			}
		});
		return sp;
	}
	
	public <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openReadWrite(byte priority) {
		ioMutex.lock();
		try {
			if (rwIO != null)
				return new AsyncWork<>(null, new Exception("Data already open for modification"));
			if (!canOpenReadWrite())
				return new AsyncWork<>(null, new Exception("Modification not supported"));
			forceCloseIOReadOnly();
			AsyncWork<T, ? extends Exception> open = openIOReadWrite(priority);
			rwIO = open;
			rwIO.listenInline(() -> {
				if (!open.isSuccessful())
					rwIO = null;
				else
					open.getResult().addCloseListener(() -> { rwIO = null; });
			});
			return open;
		} finally {
			ioMutex.unlock();
		}
	}
	
	protected abstract AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority);
	
	protected abstract boolean canOpenReadWrite();
	protected abstract <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority);
	
	/* *************************************************************************** */
	/* Properties and cached data */

	private Map<String,Object> properties = null;
	
	public CachedObject<?> useCachedData(String key, Object user) {
		return DataManager.getInstance().useCachedData(this, key, user);
	}
	public void setCachedData(String key, CachedObject<?> cachedData) {
		DataManager.getInstance().setCachedData(this, key, cachedData);
	}
	
	public void releaseAllCachedData() {
		DataManager.getInstance().releaseAll(this);
	}
	
	public Object getProperty(String key) {
		if (properties == null) return null;
		return properties.get(key);
	}
	public void setProperty(String key, Object property) {
		if (properties == null) properties = new HashMap<>(10);
		properties.put(key, property);
	}
	public Object removeProperty(String key) {
		if (properties == null) return null;
		Object prop = properties.remove(key);
		if (properties.isEmpty()) properties = null;
		return prop;
	}
	public boolean hasProperty(String key) {
		if (properties == null) return false;
		return properties.containsKey(key);
	}
	public void removeAllProperties() {
		properties = null;
	}

	
	public void releaseEverything() {
		forceCloseIOReadOnly();
		if (rwIO != null) {
			if (!rwIO.isUnblocked())
				rwIO.cancel(new CancelException("Data.releaseEverything called"));
			else if (rwIO.isSuccessful())
				try { rwIO.getResult().close(); }
				catch (Throwable t) {}
			rwIO = null;
		}
		releaseAllCachedData();
		removeAllProperties();
	}
	
	/* *************************************************************************** */
	/* Format detection */
	
	private LinkedList<DataFormat> formats = null;
	private Exception formatError = null;
	private ArrayList<Pair<DataFormatListener,Task<?,?>>> formatListeners = null;
	private Detection detection = null;
	private MutualExclusion<NoException> detectionMutex = new MutualExclusion<>();
	
	private class Detection {
		private AsyncWork<? extends IO.Readable,Exception> open;
		private AsyncWork<DataFormat,IOException> detectTask;
		private static final long PROGRESS_AMOUNT = 1000;
		private static final long STEP_OPEN = 2;
		private static final long STEP_DETECT = 700;
		private static final long STEP_SPE = 298;
		private WorkProgressImpl progress = new WorkProgressImpl(PROGRESS_AMOUNT);
		public void run(byte priority) {
			open = openReadOnly(priority);
			byte[] header = new byte[512];
			MutableInteger headerSize = new MutableInteger(0);
			open.listenInline(new Runnable() {
				@SuppressWarnings("resource")
				@Override
				public void run() {
					progress.progress(STEP_OPEN);
					IO.Readable io;
					detectionMutex.lock();
					try {
						if (open.isCancelled()) {
							progress.cancel(open.getCancelEvent());
							detection = null;
							return;
						}
						if (!open.isSuccessful()) {
							formatError = new Exception("Data is not accessible: " + open.getError().getMessage(), open.getError());
							for (Pair<DataFormatListener, Task<?,?>> listener : formatListeners)
								callDetectionError(listener.getValue1(), formatError, listener.getValue2());
							progress.error(formatError);
							formatListeners = null;
							detection = null;
							return;
						}
						io = open.getResult();
						if (io == null) {
							formatError = new Exception("Data is not accessible");
							for (Pair<DataFormatListener, Task<?,?>> listener : formatListeners)
								callDetectionError(listener.getValue1(), formatError, listener.getValue2());
							progress.error(formatError);
							formatListeners = null;
							detection = null;
							return;
						}
						detectTask = DataFormatRegistry.detect(Data.this, io, getSize(), priority, header, headerSize, progress, STEP_DETECT);
					} finally {
						detectionMutex.unlock();
					}
					Logger logger = getLogger();
					detectTask.listenInline(new Runnable() {
						@Override
						public void run() {
							detectionMutex.lock();
							try {
								if (detectTask.isCancelled()) {
									io.closeAsync();
									detection = null;
									return;
								}
								formats = new LinkedList<>();
								DataFormat format = detectTask.getResult();
								if (format != null) formats.add(format);
								formatError = detectTask.getError();
								if (formatError != null && logger.error())
									logger.error("Error detecting data format", formatError);
								if (formatError != null) {
									for (Pair<DataFormatListener, Task<?,?>> listener : formatListeners)
										callDetectionError(listener.getValue1(), formatError, listener.getValue2());
									progress.error(formatError);
									return;
								}
								if (format != null)
									for (Pair<DataFormatListener, Task<?,?>> listener : formatListeners)
										listener.setValue2(callFormatDetected(listener.getValue1(), format, listener.getValue2()));
							} finally {
								detectionMutex.unlock();
							}
							if (formats.isEmpty()) {
								io.closeAsync();
								progress.progress(STEP_SPE);
								detection = null;
								for (Pair<DataFormatListener, Task<?,?>> listener : formatListeners)
									callEndOfDetection(listener.getValue1(), listener.getValue2());
								formatListeners = null;
								return;
							}
							DataFormatRegistry.detectSpecializations(Data.this, formats.getFirst(), header, headerSize.get(), priority, new DataFormatListener() {
								@Override
								public void formatDetected(Data data, DataFormat format) {
									ArrayList<Pair<DataFormatListener, Task<?,?>>> listeners;
									detectionMutex.lock();
									try {
										listeners = new ArrayList<>(formatListeners);
										data.formats.add(format);
									} finally {
										detectionMutex.unlock();
									}
									for (Pair<DataFormatListener, Task<?,?>> listener : listeners)
										listener.setValue2(callFormatDetected(listener.getValue1(), format, listener.getValue2()));
								}
								@Override
								public void endOfDetection(Data data) {
									ArrayList<Pair<DataFormatListener, Task<?,?>>> listeners;
									detectionMutex.lock();
									try {
										listeners = new ArrayList<>(formatListeners);
										formatListeners = null;
										detection = null;
									} finally {
										detectionMutex.unlock();
									}
									for (Pair<DataFormatListener, Task<?,?>> listener : listeners)
										callEndOfDetection(listener.getValue1(), listener.getValue2());
									try { io.close(); } catch (Exception e) {}
								}
								@Override
								public void detectionError(Data data, Exception error) {
									endOfDetection(data);
								}
								@Override
								public void detectionCancelled(Data data) {
								}
							}, progress, STEP_SPE);
						}
					});
				}
			});
		}
		public void cancel() {
			open.unblockCancel(new CancelException("Data format detection cancelled"));
			if (detectTask != null)
				detectTask.unblockCancel(new CancelException("Data format detection cancelled"));
		}
	}

	public void detect(byte priority, WorkProgress progress, long work, DataFormatListener listener, SingleEvent<Void> cancel) {
		detectionMutex.lock();
		try {
			if (formatError != null) {
				if (progress != null) progress.error(formatError);
				callDetectionError(listener, formatError, null);
				return;
			}
			if (formats != null) {
				Task<?,?> task = null;
				for (DataFormat format : formats)
					task = callFormatDetected(listener, format, task);
				if (formatListeners == null) {
					if (progress != null) progress.progress(work);
					callEndOfDetection(listener, task);
					return;
				}
				if (progress != null && detection != null)
					WorkProgress.link(detection.progress, progress, work);
				formatListeners.add(new Pair<>(listener, task));
				return;
			}
			if (cancel.occured()) {
				if (progress != null) progress.cancel(new CancelException("Data format detection cancelled"));
				callDetectionCancelled(listener, null);
				return;
			}
			if (formatListeners != null) {
				formatListeners.add(new Pair<>(listener, null));
				if (progress != null && detection != null)
					WorkProgress.link(detection.progress, progress, work);
			} else {
				formatListeners = new ArrayList<>();
				formatListeners.add(new Pair<>(listener, null));
				detection = new Detection();
				if (progress != null)
					WorkProgress.link(detection.progress, progress, work);
				detection.run(priority); // TODO be able to change priority when already running
			}
			cancel.listen(new Listener<Void>() {
				@Override
				public void fire(Void event) {
					Task<?,?> previous = null;
					detectionMutex.lock();
					try {
						if (formatListeners == null) return;
						for (Iterator<Pair<DataFormatListener, Task<?,?>>> it = formatListeners.iterator(); it.hasNext(); ) {
							Pair<DataFormatListener, Task<?,?>> p = it.next();
							if (p.getValue1() == listener) {
								it.remove();
								previous = p.getValue2();
								break;
							}
						}
						if (formatListeners.isEmpty()) {
							detection.cancel();
							detection = null;
						}
					} finally {
						detectionMutex.unlock();
					}
					callDetectionCancelled(listener, previous);
				}
			});
		} finally {
			detectionMutex.unlock();
		}
	}
	
	private void callDetectionError(DataFormatListener listener, Exception error, Task<?,?> previous) {
		Task<?,?> task = new Task.Cpu.FromRunnable("Call DataDormatListener.detectionError", Task.PRIORITY_NORMAL, () -> {
			listener.detectionError(Data.this, error);
		});
		task.startOnDone(previous);
	}

	private void callDetectionCancelled(DataFormatListener listener, Task<?,?> previous) {
		Task<?,?> task = new Task.Cpu.FromRunnable("Call DataDormatListener.detectionCancelled", Task.PRIORITY_NORMAL, () -> {
			listener.detectionCancelled(Data.this);
		});
		task.startOnDone(previous);
	}
	
	private Task<?,?> callFormatDetected(DataFormatListener listener, DataFormat format, Task<?,?> previous) {
		Task<Void, NoException> task = new Task.Cpu.FromRunnable("Call DataFormatListener.formatDetected", Task.PRIORITY_NORMAL, () -> {
			listener.formatDetected(Data.this, format);
		});
		task.startOnDone(previous);
		return task;
	}
	
	private void callEndOfDetection(DataFormatListener listener, Task<?,?> previous) {
		Task<Void, NoException> task = new Task.Cpu.FromRunnable("Call DataFormatListener.endOfDetection", Task.PRIORITY_NORMAL, () -> {
			listener.endOfDetection(Data.this);
		});
		task.startOnDone(previous);
	}
	
	public DataFormat getDetectedFormat() {
		detectionMutex.lock();
		try {
			if (formats == null || formats.isEmpty()) return null;
			return formats.getLast();
		} finally {
			detectionMutex.unlock();
		}
	}
	
	public List<DataFormat> getDetectedFormats() {
		detectionMutex.lock();
		try {
			if (formats == null) return new ArrayList<>(0);
			return new ArrayList<>(formats);
		} finally {
			detectionMutex.unlock();
		}
	}
	
	public Exception getFormatDetectionError() {
		detectionMutex.lock();
		try {
			return formatError;
		} finally {
			detectionMutex.unlock();
		}
	}
	
	public void setFormat(DataFormat format) {
		detectionMutex.lock();
		try {
			this.formats = new LinkedList<>();
			formats.add(format);
		} finally {
			detectionMutex.unlock();
		}
	}
	
	public boolean isFormatDetectionDone() {
		detectionMutex.lock();
		try {
			return formatError != null || (formats != null && formatListeners == null);
		} finally {
			detectionMutex.unlock();
		}
	}
	
	public AsyncWork<DataFormat, Exception> detectFinalFormat(byte priority, WorkProgress progress, long work) {
		detectionMutex.lock();
		try {
			if (formatError != null) {
				if (progress != null) progress.error(formatError);
				return new AsyncWork<>(null, formatError);
			}
			if (formats != null && formatListeners == null) {
				if (progress != null) progress.progress(work);
				return new AsyncWork<>(getDetectedFormat(), null);
			}
		} finally {
			detectionMutex.unlock();
		}
		AsyncWork<DataFormat, Exception> result = new AsyncWork<>();
		SingleEvent<Void> cancel = new SingleEvent<>();
		detect(priority, progress, work, new DataFormatListener() {
			@Override
			public void formatDetected(Data data, DataFormat format) {
			}
			@Override
			public void detectionError(Data data, Exception error) {
				result.error(error);
			}
			@Override
			public void detectionCancelled(Data data) {
				result.cancel(new CancelException("Detection cancelled"));
			}
			@Override
			public void endOfDetection(Data data) {
				result.unblockSuccess(getDetectedFormat());
			}
		}, cancel);
		result.onCancel((ev) -> { cancel.fire(null); });
		return result;
	}
	
	/** Must be used only in a situation asynchronous detection cannot be used. */
	public DataFormat detectFormatSync() {
		AsyncWork<DataFormat, Exception> f = detectFinalFormat(Task.PRIORITY_URGENT, null, 0);
		try {
			return f.blockResult(0);
		} catch (Throwable t) {
			return null;
		}
	}
	
}
