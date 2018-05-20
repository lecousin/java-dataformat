package net.lecousin.dataformat.archive.tar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.core.hierarchy.DirectoryData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public class TarDataFormat extends ArchiveDataFormat {

	public static final TarDataFormat instance = new TarDataFormat();
	
	private TarDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("TAR");
	}
	
	public static final String[] extensions = { "tar" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	public static final String[] mimes = { "application/x-tar" };
	
	@Override
	public String[] getMIMETypes() {
		return mimes;
	}
	
	public static OpenedDataCache<TarFile> cache = new OpenedDataCache<TarFile>(TarFile.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<TarFile,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			TarFile tar = new TarFile((IO.Readable.Seekable)io, progress, work);
			ISynchronizationPoint<IOException> sp = tar.getSynchOnReady();
			AsyncWork<TarFile,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(tar);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(TarFile tar) {
			try { tar.close(); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncWork<TarDataFormatInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<TarDataFormatInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<TarFile>,Exception> get = cache.open(data, this, priority, null, 0);
		get.listenInline(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				sp.unblockSuccess(createInfo(get.getResult().get()));
				get.getResult().release(TarDataFormat.this);
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				get.unblockCancel(event);
			}
		});
		return sp;
	}
	
	private static TarDataFormatInfo createInfo(TarFile tar) {
		TarDataFormatInfo info = new TarDataFormatInfo();
		info.nbFiles = new Long(tar.getEntries().size());
		return info;
	}

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		Data tarData = container;
		while (tarData instanceof DirectoryData)
			tarData = ((DirectoryData)tarData).getContainer();
		WorkProgress progress = new WorkProgressImpl(1000, "Reading tar content");
		AsyncWork<CachedObject<TarFile>,Exception> getCache = cache.open(tarData, this, Task.PRIORITY_IMPORTANT, progress, 800);
		Data tar = tarData;
		new Task.Cpu.FromRunnable("List zip content", Task.PRIORITY_IMPORTANT, () -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening TAR file", getCache.getError());
				return;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return;
			}
			listSubData(tar, container, listener, getCache.getResult().get(), progress);
			getCache.getResult().release(TarDataFormat.this);
		}).startOn(getCache, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
	private void listSubData(Data tarData, Data container, CollectionListener<Data> listener, TarFile tar, WorkProgress progress) {
		String path;
		if (container instanceof DirectoryData) {
			path = container.getName().appLocalizationSync() + '/';
			Data parent = container.getContainer();
			while (parent instanceof DirectoryData) {
				path = parent.getName().appLocalizationSync() + '/' + path;
				parent = parent.getContainer();
			}
		} else
			path = "";
		LinkedList<Data> content = new LinkedList<>();
		ArrayList<String> dirs = new ArrayList<>();
		for (TarEntry f : tar.getEntries()) {
			String name = f.getPath();
			boolean dir = f.isDirectory();
			int i = name.lastIndexOf('/');
			if (i < 0) {
				if (path.length() == 0) {
					if (dir) {
						if (!dirs.contains(name)) {
							content.add(new DirectoryData(container, this, new FixedLocalizedString(name)));
							dirs.add(name);
						}
					} else {
						content.add(new SubData(tarData, f.getPosition(), f.getDataSize(), new FixedLocalizedString(f.getName()), null));
					}
				}
				continue;
			}
			String p = name.substring(0, i + 1);
			name = name.substring(i + 1);
			if (p.equals(path)) {
				if (dir) {
					if (!dirs.contains(name)) {
						content.add(new DirectoryData(container, this, new FixedLocalizedString(name)));
						dirs.add(name);
					}
				} else {
					content.add(new SubData(tarData, f.getPosition(), f.getDataSize(), new FixedLocalizedString(f.getName()), p.substring(0, p.length() - 1)));
				}
				continue;
			}
			if (path.length() == 0) {
				i = p.indexOf('/');
				name = p.substring(0, i);
				if (!dirs.contains(name)) {
					content.add(new DirectoryData(container, this, new FixedLocalizedString(name)));
					dirs.add(name);
				}
				continue;
			}
			if (p.startsWith(path)) {
				p = p.substring(path.length());
				i = p.indexOf('/');
				if (i > 0) {
					name = p.substring(0, i);
					if (!dirs.contains(name)) {
						content.add(new DirectoryData(container, this, new FixedLocalizedString(name)));
						dirs.add(name);
					}
				}
			}
		}
		listener.elementsReady(content);
		progress.done();
	}

	@Override
	public Class<DataCommonProperties> getSubDataCommonProperties() {
		return null; // TODO
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

}
