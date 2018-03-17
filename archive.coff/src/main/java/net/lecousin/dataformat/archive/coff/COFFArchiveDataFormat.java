package net.lecousin.dataformat.archive.coff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.archive.ArchiveDataInfo;
import net.lecousin.dataformat.archive.coff.COFFArchive.COFFFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

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
		protected AsyncWork<COFFArchive,Exception> open(Data data, Readable io, WorkProgress progress, long work) {
			COFFArchive archive = new COFFArchive();
			archive.scanContent(io, null, progress, work); // TODO be able to use an async collection
			AsyncWork<COFFArchive,Exception> result = new AsyncWork<>();
			SynchronizationPoint<IOException> sp = archive.contentReady();
			sp.listenInline(new Runnable() {
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
	public AsyncWork<ArchiveDataInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<ArchiveDataInfo, Exception> result = new AsyncWork<ArchiveDataInfo, Exception>();
		cache.open(data, this, priority, null, 0).listenInline(new AsyncWorkListener<CachedObject<COFFArchive>, Exception>() {
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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		cache.open(data, COFFArchiveDataFormat.this, Task.PRIORITY_NORMAL, null, 0).listenInline(new AsyncWorkListener<CachedObject<COFFArchive>, Exception>() {
			@Override
			public void ready(CachedObject<COFFArchive> cache) {
				COFFArchive archive = cache.get();
				if (archive == null) {
					COFFArchive.logger.error("Unable to read COFF Archive");
					list.done();
					cache.release(COFFArchiveDataFormat.this);
					return;
				}
				ArrayList<Data> files = new ArrayList<>(archive.getContent().size());
				for (COFFFile file : archive.getContent())
					files.add(new COFFArchiveSubData(data, file));
				list.newElements(files);
				list.done();
				cache.release(COFFArchiveDataFormat.this);
			}
			@Override
			public void error(Exception error) {
				COFFArchive.logger.error("Unable to read COFF Archive", error);
				list.done();
			}
			@Override
			public void cancelled(CancelException event) {
				list.done();
			}
		});
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> addSubData(Data data, List<Pair<String, IOProvider.Readable>> subData, byte priority) {
		return null;
	}
	
	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}
	
	@Override
	public ISynchronizationPoint<Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}

}
