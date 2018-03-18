package net.lecousin.dataformat.archive.cab;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.archive.ArchiveDataInfo;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;

public class CabDataFormat extends ArchiveDataFormat implements DataFormat.DataContainerFlat {

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
		protected AsyncWork<CabFile,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			CabFile cab = CabFile.openReadOnly(io, progress, work);
			SynchronizationPoint<IOException> sp = cab.onLoaded();
			AsyncWork<CabFile,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
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
	public AsyncWork<ArchiveDataInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<CachedObject<CabFile>, Exception> c = cache.open(data, this, priority, null, 0);
		AsyncWork<ArchiveDataInfo, Exception> sp = new AsyncWork<>();
		c.listenInline(new Runnable() {
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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<CabFile>, Exception> c = cache.open(data, this, Task.PRIORITY_NORMAL, null, 0);
		c.listenInline(new Runnable() {
			@Override
			public void run() {
				if (c.hasError()) { list.done(); return; }
				if (c.isCancelled()) { list.done(); return; }
				CabFile cab = c.getResult().get();
				ArrayList<Data> subData = new ArrayList<>(cab.getFiles().size());
				for (CabFile.File file : cab.getFiles()) {
					subData.add(new CabFileData(data, file));
				}
				list.newElements(subData);
				list.done();
				c.getResult().release(CabDataFormat.this);
			}
		});
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
