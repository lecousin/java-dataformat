package net.lecousin.dataformat.archive.coff;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.archive.ArchiveDataInfo;
import net.lecousin.dataformat.archive.coff.COFFArchive.COFFFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public class COFFArchiveDataFormat extends ArchiveDataFormat {

	public static final COFFArchiveDataFormat instance = new COFFArchiveDataFormat();

	// https://en.wikipedia.org/wiki/Ar_(Unix)
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("COFF Archive");
	}
	
	public static OpenedDataCache<COFFArchive> cache = new OpenedDataCache<COFFArchive>(COFFArchive.class, 5*60*1000) {

		@Override
		protected AsyncSupplier<COFFArchive,Exception> open(Data data, Readable io, WorkProgress progress, long work) {
			COFFArchive archive = new COFFArchive();
			archive.scanContent(io, null, progress, work); // TODO be able to use an async collection
			AsyncSupplier<COFFArchive,Exception> result = new AsyncSupplier<>();
			Async<IOException> sp = archive.contentReady();
			sp.onDone(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(archive);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(COFFArchive object) {
		}
		
	};

	@Override
	public AsyncSupplier<ArchiveDataInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<ArchiveDataInfo, Exception> result = new AsyncSupplier<ArchiveDataInfo, Exception>();
		cache.open(data, this, priority, null, 0).listen(new Listener<CachedObject<COFFArchive>, Exception>() {
			@Override
			public void ready(CachedObject<COFFArchive> cache) {
				if (cache == null) {
					result.unblockSuccess(null);
					return;
				}
				COFFArchive a = cache.get();
				ArchiveDataInfo info = new ArchiveDataInfo();
				info.nbFiles = new Long(a.getContent().size());
				result.unblockSuccess(info);
				cache.release(COFFArchiveDataFormat.this);
			}
			@Override
			public void error(Exception error) {
				result.unblockError(error);
			}
			@Override
			public void cancelled(CancelException event) {
				result.unblockCancel(event);
			}
		});
		return result;
	}

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading COFF archive");
		AsyncSupplier<CachedObject<COFFArchive>, Exception> get = cache.open(container, COFFArchiveDataFormat.this, Priority.NORMAL, progress, 800);
		Task.cpu("Loading COFF", Priority.IMPORTANT, t -> {
			if (get.hasError()) {
				listener.error(get.getError());
				progress.error(get.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening COFF archive", get.getError());
				return null;
			}
			if (get.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			COFFArchive archive = get.getResult().get();
			if (archive == null) {
				COFFArchive.getLogger().error("Unable to read COFF Archive");
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				get.getResult().release(COFFArchiveDataFormat.this);
				return null;
			}
			ArrayList<Data> files = new ArrayList<>(archive.getContent().size());
			for (COFFFile file : archive.getContent())
				files.add(new COFFArchiveSubData(container, file));
			listener.elementsReady(files);
			progress.done();
			get.getResult().release(COFFArchiveDataFormat.this);
			return null;
		}).startOn(get, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

}
