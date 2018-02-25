package net.lecousin.dataformat.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.event.SingleEvent;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public abstract class Data {
	
	public static final Log logger = LogFactory.getLog("net.lecousin.dataformat");
	
	public abstract String getName();
	public abstract String getDescription();
	public abstract long getSize();
	
	/** returns true if it has data inside, false if this is just a container but no data to analyze such as a file system directory. */
	public abstract boolean hasContent();
	public abstract Data getContainer();
	
	/* IO Management:
	 * The data may be accessed concurrently, with different requirements.
	 * The source may be very limited (like compressed stream is only readable, not seekable...).
	 * The goal here is to provide most convenient way of accessing the data content
	 */
	
	private AsyncWork<Void, Exception> ioLoading = null;
	private IO io = null;
	private int ioUsage = 0;
	private Task<Void,NoException> closeIO = null;
	
	public void forceCloseAllIO() {
		synchronized (this) {
			if (closeIO != null) {
				closeIO.cancel(new CancelException("forceCloseAllIO called"));
				closeIO = null;
			}
			if (ioLoading != null) {
				ioLoading.cancel(new CancelException("forceCloseAllIO called"));
				ioLoading = null;
			}
			if (io != null) {
				try { io.close(); } catch (Throwable t) {}
				io = null;
			}
		}
	}
	
	private AsyncWork<Void, Exception> prepareIO(byte priority) {
		synchronized (this) {
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
				ioLoading = new AsyncWork<Void, Exception>();
				AsyncWork<IO,? extends Exception> originalIO = openIO(priority);
				if (originalIO == null) {
					ioLoading = null;
					return new AsyncWork<>(null,null);
				}
				ioLoading.onCancel(new Listener<CancelException>() {
					@Override
					public void fire(CancelException event) {
						originalIO.unblockCancel(event);
					}
				});
				originalIO.listenInline(new Runnable() {
					@Override
					public void run() {
						synchronized (Data.this) {
							if (originalIO.isCancelled() || ioLoading == null)
								return;
							if (!originalIO.isSuccessful()) {
								ioLoading.unblockError(originalIO.getError());
								return;
							}
							IO oio = originalIO.getResult();
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
									try { io = new BufferedIO.ReadOnly((IO.Readable.Seekable)oio, 512, 16384, getSize(), false); }
									catch (IOException e) {
										ioLoading.unblockError(e);
										return;
									}
								}
							} else {
								// only streaming, let's make it better
								try { io = new ReadableToSeekable((IO.Readable)oio, 16384); }
								catch (IOException e) {
									ioLoading.unblockError(e);
									return;
								}
							}
							ioLoading.unblockSuccess(null);
						}
					}
				});
			}
			ioUsage++;
			AsyncWork<Void,Exception> sp = new AsyncWork<>();
			sp.onCancel(new Listener<CancelException>() {
				@Override
				public void fire(CancelException event) {
					// caller requested to cancel
					synchronized (Data.this) {
						if (ioUsage == 1) {
							if (ioLoading != null) {
								ioLoading.unblockCancel(event);
								if (!ioLoading.isCancelled())
									releaseIO();
								else {
									ioLoading = null;
									ioUsage = 0;
								}
							}
						} else
							ioUsage--;
					}
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
		}
	}
	private void releaseIO() {
		synchronized (this) {
			ioUsage--;
			if (ioUsage == 0) {
				ioLoading = null;
				closeIO = new Task.Cpu<Void,NoException>("Closing data IO", Task.PRIORITY_RATHER_LOW) {
					@Override
					public Void run() {
						synchronized (Data.this) {
							if (!isCancelled() && ioUsage == 0) {
								try { io.close(); } catch (Throwable t) {}
								io = null;
							}
							closeIO = null;
						}
						return null;
					}
				};
				closeIO.executeIn(5000);
				closeIO.start();
			}
		}
	}
	
	public <T extends IO.Readable.Seekable&IO.Readable.Buffered&IO.KnownSize> AsyncWork<T,Exception> open(byte priority) {
		AsyncWork<Void, Exception> prepare = prepareIO(priority);
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
				@SuppressWarnings("resource")
				SubIO.Readable.Seekable.Buffered wrap = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)io, 0, getSize(), io.getSourceDescription(), false);
				wrap.addCloseListener(new Runnable() {
					@Override
					public void run() {
						releaseIO();
					}
				});
				sp.unblockSuccess((T)wrap);
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
	
	protected abstract AsyncWork<IO,? extends Exception> openIO(byte priority);
	
	
	private DataFormat format = null;
	private Exception formatError = null;
	private ArrayList<DataFormatListener> formatListeners = null;
	private Detection detection = null;
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
		forceCloseAllIO();
		releaseAllCachedData();
		removeAllProperties();
	}
	
	private class Detection {
		private AsyncWork<? extends IO.Readable,Exception> open;
		private AsyncWork<DataFormat,IOException> detectTask;
		private static final long PROGRESS_AMOUNT = 1000;
		private static final long STEP_OPEN = 2;
		private static final long STEP_DETECT = 700;
		private static final long STEP_SPE = 298;
		private WorkProgressImpl progress = new WorkProgressImpl(PROGRESS_AMOUNT);
		public void run(byte priority) {
			open = open(priority);
			byte[] header = new byte[512];
			MutableInteger headerSize = new MutableInteger(0);
			open.listenInline(new Runnable() {
				@SuppressWarnings("resource")
				@Override
				public void run() {
					progress.progress(STEP_OPEN);
					IO.Readable io;
					synchronized (Data.this) {
						if (open.isCancelled()) {
							detection = null;
							return;
						}
						if (!open.isSuccessful()) {
							formatError = new Exception("Data is not accessible: " + open.getError().getMessage(), open.getError());
							for (DataFormatListener listener : formatListeners)
								listener.detectionError(Data.this, formatError);
							formatListeners = null;
							detection = null;
							return;
						}
						io = open.getResult();
						if (io == null) {
							formatError = new Exception("Data is not accessible");
							for (DataFormatListener listener : formatListeners)
								listener.detectionError(Data.this, formatError);
							formatListeners = null;
							detection = null;
							return;
						}
						detectTask = DataFormatRegistry.detect(Data.this, io, getSize(), priority, header, headerSize, progress, STEP_DETECT);
					}
					detectTask.listenInline(new Runnable() {
						@Override
						public void run() {
							synchronized (Data.this) {
								if (detectTask.isCancelled()) {
									io.closeAsync();
									detection = null;
									return;
								}
								format = detectTask.getResult();
								formatError = detectTask.getError();
								if (formatError != null && logger.isErrorEnabled())
									logger.error("Error detecting data format", formatError);
								if (formatError != null) {
									for (DataFormatListener listener : formatListeners)
										listener.detectionError(Data.this, formatError);
								} else {
									for (DataFormatListener listener : formatListeners)
										listener.formatDetected(Data.this, format);
								}
							}
							if (format == null) {
								io.closeAsync();
								progress.progress(STEP_SPE);
								detection = null;
								for (DataFormatListener listener : formatListeners)
									listener.endOfDetection(Data.this);
								formatListeners = null;
								return;
							}
							DataFormatRegistry.detectSpecializations(Data.this, format, header, headerSize.get(), priority, new DataFormatListener() {
								@Override
								public void formatDetected(Data data, DataFormat format) {
									ArrayList<DataFormatListener> listeners;
									synchronized (data) {
										listeners = new ArrayList<>(formatListeners);
										data.format = format;
									}
									for (DataFormatListener listener : listeners)
										listener.formatDetected(data, format);
								}
								@Override
								public void endOfDetection(Data data) {
									ArrayList<DataFormatListener> listeners;
									synchronized (data) {
										listeners = new ArrayList<>(formatListeners);
										formatListeners = null;
										detection = null;
									}
									for (DataFormatListener listener : listeners)
										listener.endOfDetection(data);
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
		synchronized (this) {
			if (formatError != null) {
				listener.detectionError(this, formatError);
				return;
			}
			if (format != null) {
				listener.formatDetected(this, format);
				if (formatListeners == null) {
					listener.endOfDetection(this);
					return;
				}
				formatListeners.add(listener);
				return;
			}
			if (cancel.occured()) {
				listener.detectionCancelled(this);
				return;
			}
			if (formatListeners != null)
				formatListeners.add(listener);
			else {
				formatListeners = new ArrayList<>();
				formatListeners.add(listener);
				detection = new Detection();
				detection.run(priority); // TODO be able to change priority when already running
			}
			if (progress != null && detection != null)
				WorkProgress.link(detection.progress, progress, work);
			cancel.listen(new Listener<Void>() {
				@Override
				public void fire(Void event) {
					synchronized (Data.this) {
						if (formatListeners == null) return;
						formatListeners.remove(listener);
						if (formatListeners.isEmpty()) {
							detection.cancel();
							detection = null;
						}
					}
					listener.detectionCancelled(Data.this);
				}
			});
		}
	}
	public DataFormat getDetectedFormat() { return format; }
	public void setFormat(DataFormat format) {
		this.format = format;
	}
	
}
