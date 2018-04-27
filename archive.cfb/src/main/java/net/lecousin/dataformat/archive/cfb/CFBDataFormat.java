package net.lecousin.dataformat.archive.cfb;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.archive.ArchiveDataInfo;
import net.lecousin.dataformat.archive.cfb.CFBFile.CFBSubFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public class CFBDataFormat extends ArchiveDataFormat {

	public static final CFBDataFormat instance = new CFBDataFormat();
	protected CFBDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Microsoft Compound File Binary");
	}
	
	public static final String[] extensions = new String[] {};
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
	public static OpenedDataCache<CFBFile> cache = new OpenedDataCache<CFBFile>(CFBFile.class, 5*60*1000) {
		
		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<CFBFile,Exception> open(Data data, Readable io, WorkProgress progress, long work) {
			CFBFile cfb;
			try { cfb = new CFBFile((IO.Readable.Seekable&IO.KnownSize)io, true /* TODO */, progress, work); }
			catch (Exception e) {
				return new AsyncWork<>(null, e);
			}
			ISynchronizationPoint<IOException> sp = cfb.getSynchOnReady();
			AsyncWork<CFBFile,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(cfb);
				}
			});
			return result;
		}
		
		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}
		
		@Override
		protected void close(CFBFile cfb) {
			try { cfb.close(); }
			catch (Throwable t) {}
		}
	};
	
	@Override
	public AsyncWork<? extends DataFormatInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<ArchiveDataInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<CFBFile>,Exception> get = cache.open(data, this, priority/*, true*/, null, 0);
		get.listenInline(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				CachedObject<CFBFile> cfb = get.getResult();
				cfb.get().getSynchOnReady().listenInline(new Runnable() {
					@Override
					public void run() {
						if (cfb.get().getSynchOnReady().hasError()) {
							CFBFile.getLogger().error("Error reading CFB file", cfb.get().getSynchOnReady().getError());
							sp.unblockError(cfb.get().getSynchOnReady().getError());
							cfb.release(CFBDataFormat.this);
							return;
						}
						ArchiveDataInfo info = new ArchiveDataInfo();
						info.nbFiles = new Long(cfb.get().getContent().size());
						cfb.release(CFBDataFormat.this);
						sp.unblockSuccess(info);
					}
				});
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
		WorkProgress progress = new WorkProgressImpl(1000, "Reading CFB file");
		AsyncWork<CachedObject<CFBFile>,Exception> get = cache.open(container, this, Task.PRIORITY_IMPORTANT/*, true*/, progress, 800);
		new Task.Cpu.FromRunnable("Loading CFB", Task.PRIORITY_IMPORTANT, () -> {
			if (get.hasError()) {
				listener.error(get.getError());
				progress.error(get.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening CFB file", get.getError());
				return;
			}
			if (get.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return;
			}
			CachedObject<CFBFile> cfb = get.getResult();
			cfb.get().getSynchOnReady().listenInline(new Runnable() {
				@Override
				public void run() {
					if (cfb.get().getSynchOnReady().hasError()) {
						CFBFile.getLogger().error("Error reading CFB file", cfb.get().getSynchOnReady().getError());
						listener.error(cfb.get().getSynchOnReady().getError());
						progress.error(cfb.get().getSynchOnReady().getError());
						cfb.release(CFBDataFormat.this);
						return;
					}
					try {
						ArrayList<CFBSubFile> files = cfb.get().getContent();
						ArrayList<Data> dataList = new ArrayList<>(files.size());
						for (CFBSubFile f : files)
							dataList.add(new CFBSubFileData(container, f));
						listener.elementsReady(dataList);
						progress.done();
					} finally {
						cfb.release(CFBDataFormat.this);
					}
				}
			});
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
