package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class ExtFSDataFormat implements ContainerDataFormat {

	public static final ExtFSDataFormat instance = new ExtFSDataFormat();
	
	private ExtFSDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Ext File System");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.filesystem.ext/images/hd-linux-", ".png", 16, 22, 32, 128);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
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
	public AsyncWork<DataFormatInfo, Exception> getInfo(Data data, byte priority) {
		return null;
	}

	public static OpenedDataCache<ExtFS> cache = new OpenedDataCache<ExtFS>(ExtFS.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<ExtFS, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncWork<ExtFS, Exception> result = new AsyncWork<>();
			IO.Readable.Seekable content;
			if (io instanceof IO.Readable.Seekable)
				content = (IO.Readable.Seekable)io;
			else
				try { content = new ReadableToSeekable(io, 65536); }
				catch (IOException e) {
					result.error(e);
					return result;
				}
			ExtFS fs = new ExtFS(content);
			fs.open().listenInlineSP(() -> { result.unblockSuccess(fs); }, result);
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(ExtFS fs) {
			try { fs.close(); }
			catch (Exception e) {}
		}
		
	};

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading Ext File System");
		AsyncWork<CachedObject<ExtFS>, Exception> getCache = cache.open(container, this, Task.PRIORITY_IMPORTANT, progress, 800);
		new Task.Cpu.FromRunnable("Get Ext File System root directory content", Task.PRIORITY_IMPORTANT, () -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system", getCache.getError());
				return;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return;
			}
			ExtFS fs = getCache.getResult().get();
			AsyncWork<List<ExtFSEntry>, IOException> entries = fs.getRoot().getEntries();
			entries.listenInline(() -> {
				List<Data> list = new ArrayList<>();
				if (entries.hasError()) {
					listener.error(entries.getError());
					progress.error(entries.getError());
					LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system root directory", entries.getError());
				} else {
					if (entries.isSuccessful()) {
						for (ExtFSEntry entry : entries.getResult())
							list.add(new ExtFSData(container, entry));
					}
					listener.elementsReady(list);
					progress.done();
				}
				getCache.getResult().release(ExtFSDataFormat.this);
			});
		}).startOn(getCache, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
	public WorkProgress listenDirectorySubData(ExtFSData container, CollectionListener<Data> listener) {
		Data fsData = container;
		LinkedList<String> path = new LinkedList<>();
		while (fsData.getDetectedFormat() != ExtFSDataFormat.instance) {
			path.addFirst(fsData.getName().appLocalizationSync());
			fsData = fsData.getContainer();
		}
		
		WorkProgress progress = new WorkProgressImpl(1000, "Reading Ext File System");
		AsyncWork<CachedObject<ExtFS>, Exception> getCache = cache.open(fsData, this, Task.PRIORITY_IMPORTANT, progress, 500);
		new Task.Cpu.FromRunnable("Get Ext File System directory content", Task.PRIORITY_IMPORTANT, () -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system", getCache.getError());
				return;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return;
			}
			ExtFS fs = getCache.getResult().get();
			if (fs != container.entry.getFS()) {
				// new instance of ExtFS, we need to get a new instance of the directory
				fs.loadDirectory(path).listenInline(
					(dir) -> {
						loadDirectoryEntries(dir, getCache.getResult(), container, listener, progress);
					},
					(error) -> {
						listener.error(error);
						progress.error(error);
						getCache.getResult().release(ExtFSDataFormat.this);
					},
					(cancel) -> {
						listener.elementsReady(new ArrayList<>(0));
						progress.done();
						getCache.getResult().release(ExtFSDataFormat.this);
					}
				);
				return;
			}
			loadDirectoryEntries((ExtDirectory)container.entry, getCache.getResult(), container, listener, progress);
		}).startOn(getCache, true);
		return progress;
	}
	
	private void loadDirectoryEntries(ExtDirectory dir, CachedObject<ExtFS> cache, Data container, CollectionListener<Data> listener, WorkProgress progress) {
		AsyncWork<List<ExtFSEntry>, IOException> entries = dir.getEntries();
		entries.listenInline(() -> {
			List<Data> list = new ArrayList<>();
			if (entries.hasError()) {
				listener.error(entries.getError());
				progress.error(entries.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system root directory", entries.getError());
			} else {
				if (entries.isSuccessful()) {
					for (ExtFSEntry entry : entries.getResult())
						list.add(new ExtFSData(container, entry));
				}
				listener.elementsReady(list);
				progress.done();
			}
			cache.release(ExtFSDataFormat.this);
		});
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		p.size = Long.valueOf(((ExtFSData)subData).getSize());
		return p;
	}

}
