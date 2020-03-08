package net.lecousin.dataformat.archive.cab;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.archive.ArchiveDataInfo;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public class CabDataFormat extends ArchiveDataFormat {

	public static final CabDataFormat instance = new CabDataFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Cabinet Archive");
	}

	public static final String[] extensions = { "cab" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	public static final String[] mimes = { "application/vnd.ms-cab-compressed" };

	@Override
	public String[] getMIMETypes() {
		return mimes;
	}
	
	public static OpenedDataCache<CabFile> cache = new OpenedDataCache<CabFile>(CabFile.class, 5*60*1000) {

		@Override
		protected AsyncSupplier<CabFile,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			CabFile cab = CabFile.openReadOnly(io, progress, work);
			Async<IOException> sp = cab.onLoaded();
			AsyncSupplier<CabFile,Exception> result = new AsyncSupplier<>();
			sp.onDone(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(cab);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(CabFile cab) {
			cab.close();
		}
		
	};

	@Override
	public AsyncSupplier<ArchiveDataInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<CachedObject<CabFile>, Exception> c = cache.open(data, this, priority, null, 0);
		AsyncSupplier<ArchiveDataInfo, Exception> sp = new AsyncSupplier<>();
		c.onDone(new Runnable() {
			@Override
			public void run() {
				if (c.hasError()) { sp.error(c.getError()); return; }
				if (c.isCancelled()) { sp.cancel(c.getCancelEvent()); return; }
				CabFile cab = c.getResult().get();
				ArchiveDataInfo info = new ArchiveDataInfo();
				info.nbFiles = Long.valueOf(cab.getFiles().size());
				sp.unblockSuccess(info);
				c.getResult().release(CabDataFormat.this);
			}
		});
		return sp;
	}

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading CAB");
		AsyncSupplier<CachedObject<CabFile>,Exception> get = cache.open(container, this, Task.Priority.IMPORTANT, progress, 800);
		Task.cpu("Loading CAB", Task.Priority.IMPORTANT, task -> {
			if (get.hasError()) {
				listener.error(get.getError());
				progress.error(get.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening CAB file", get.getError());
				return null;
			}
			if (get.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			CabFile cab = get.getResult().get();
			ArrayList<Data> subData = new ArrayList<>(cab.getFiles().size());
			for (CabFile.File file : cab.getFiles()) {
				subData.add(new CabFileData(container, file));
			}
			listener.elementsReady(subData);
			progress.done();
			get.getResult().release(CabDataFormat.this);
			return null;
		}).startOn(get, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null; // TODO
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO
		return null;
	}

}
