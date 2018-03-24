package net.lecousin.dataformat.archive.rar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class RarDataFormat extends ArchiveDataFormat {

	public static final RarDataFormat instance = new RarDataFormat();
	
	// http://www.forensicswiki.org/wiki/RAR
	// http://www.forensicswiki.org/w/images/5/5b/RARFileStructure.txt
	// http://www.rarlab.com/technote.htm
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("RAR Archive");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/archive/rar/rar_", ".png", 16, 32, 48, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "rar" };
	}
	
	@Override
	public String[] getMIMETypes() {
		return new String[] { "application/x-rar-compressed" };
	}
	
	public static OpenedDataCache<RarArchive> cache = new OpenedDataCache<RarArchive>(RarArchive.class, 5*60*1000) {

		@Override
		protected AsyncWork<RarArchive,Exception> open(Data data, Readable io, WorkProgress progress, long work) {
			RarArchive rar = new RarArchive((IO.Readable.Seekable)io);
			SynchronizationPoint<IOException> sp = rar.loadContent(progress, work);
			AsyncWork<RarArchive,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(rar);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(RarArchive rar) {
			try { rar.close(true); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncWork<RarDataInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<RarDataInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<RarArchive>,Exception> get = cache.open(data, this, priority, null, 0);
		get.listenInline(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				RarArchive rar = get.getResult().get();
				RarDataInfo info = new RarDataInfo();
				info.nbFiles = new Long(rar.content.size());
				sp.unblockSuccess(info);
				get.getResult().release(RarDataFormat.this);
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
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading RAR");
		AsyncWork<CachedObject<RarArchive>,Exception> get = cache.open(container, this, Task.PRIORITY_IMPORTANT, progress, 800);
		new Task.Cpu.FromRunnable("Loading Rar", Task.PRIORITY_IMPORTANT, () -> {
			if (get.hasError()) {
				listener.error(get.getError());
				progress.error(get.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening RAR file", get.getError());
				return;
			}
			if (get.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return;
			}
			CachedObject<RarArchive> rar = get.getResult();
			try {
				Collection<RarArchive.RARFile> files = rar.get().getContent();
				ArrayList<Data> dataList = new ArrayList<>(files.size());
				for (RarArchive.RARFile f : files)
					dataList.add(new RarFileData(container, f));
				listener.elementsReady(dataList);
				progress.done();
			} finally {
				rar.release(RarDataFormat.this);
			}
		}).startOn(get, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
	@Override
	public Class<DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

}
