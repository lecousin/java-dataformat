package net.lecousin.dataformat.archive.rar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<RarArchive>,Exception> get = cache.open(data, this, Task.PRIORITY_IMPORTANT, null, 0);
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Loading Rar", Task.PRIORITY_IMPORTANT) {
			@Override
			public Void run() {
				if (!get.isSuccessful()) {
					list.done();
					getApplication().getDefaultLogger().error("Error opening RAR file", get.getError());
					return null;
				}
				CachedObject<RarArchive> rar = get.getResult();
				try {
					Collection<RarArchive.RARFile> files = rar.get().getContent();
					ArrayList<Data> dataList = new ArrayList<>(files.size());
					for (RarArchive.RARFile f : files)
						dataList.add(new RarFileData(data, f));
					list.newElements(dataList);
					list.done();
					return null;
				} finally {
					rar.release(RarDataFormat.this);
				}
			}
		};
		task.startOn(get, true);
	}
	
	
	@Override
	public Class<DataCommonProperties> getSubDataCommonProperties() {
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
