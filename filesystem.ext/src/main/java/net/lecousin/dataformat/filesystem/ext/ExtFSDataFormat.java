package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.dataformat.filesystem.ext.ExtFS.INode;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
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
	
	// source: http://www.iconarchive.com/show/nuoveXT-icons-by-saki/Filesystems-hd-linux-icon.html (GPL)
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.filesystem.ext/images/hd-linux-", ".png", 16, 24, 32, 48, 64, 96, 128);
	
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
	public AsyncSupplier<ExtFSDataFormatInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<CachedObject<ExtFS>, Exception> getCache = cache.open(data, this, priority, null, 0);
		AsyncSupplier<ExtFSDataFormatInfo, Exception> result = new AsyncSupplier<>();
		getCache.thenStart("Get Ext File System information", priority, () -> {
			ExtFS fs = getCache.getResult().get();
			ExtFSDataFormatInfo info = new ExtFSDataFormatInfo();
			info.blockSize = fs.blockSize;
			info.blocksPerGroup = fs.blocksPerGroup;
			info.inodesPerGroup = fs.inodesPerGroup;
			info.inodeSize = fs.inodeSize;
			info.uuid = fs.uuid;
			result.unblockSuccess(info);
		}, result);
		return result;
	}

	public static OpenedDataCache<ExtFS> cache = new OpenedDataCache<ExtFS>(ExtFS.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncSupplier<ExtFS, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncSupplier<ExtFS, Exception> result = new AsyncSupplier<>();
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
			fs.open().onDone(() -> { result.unblockSuccess(fs); }, result, e -> e);
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
		AsyncSupplier<CachedObject<ExtFS>, Exception> getCache = cache.open(container, this, Priority.IMPORTANT, progress, 800);
		Task.cpu("Get Ext File System root directory content", Priority.IMPORTANT, t -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system", getCache.getError());
				return null;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			ExtFS fs = getCache.getResult().get();
			AsyncSupplier<List<ExtFSEntry>, IOException> entries = fs.getRoot().getEntries();
			entries.onDone(() -> {
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
			return null;
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
		AsyncSupplier<CachedObject<ExtFS>, Exception> getCache = cache.open(fsData, this, Priority.IMPORTANT, progress, 500);
		Task.cpu("Get Ext File System directory content", Priority.IMPORTANT, t -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading Ext file system", getCache.getError());
				return null;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			ExtFS fs = getCache.getResult().get();
			if (fs != container.entry.getFS()) {
				// new instance of ExtFS, we need to get a new instance of the directory
				fs.loadDirectory(path).onDone(
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
				return null;
			}
			loadDirectoryEntries((ExtDirectory)container.entry, getCache.getResult(), container, listener, progress);
			return null;
		}).startOn(getCache, true);
		return progress;
	}
	
	private void loadDirectoryEntries(ExtDirectory dir, CachedObject<ExtFS> cache, Data container, CollectionListener<Data> listener, WorkProgress progress) {
		AsyncSupplier<List<ExtFSEntry>, IOException> entries = dir.getEntries();
		entries.onDone(() -> {
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
	public Class<ExtFSEntryProperties> getSubDataCommonProperties() {
		return ExtFSEntryProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		ExtFSData d = (ExtFSData)subData;
		AsyncSupplier<INode, IOException> load = d.entry.loadINode();
		ExtFSEntryProperties p = new ExtFSEntryProperties();
		p.size = BigInteger.valueOf(d.getSize());
		load.block(0);
		if (load.isSuccessful()) {
			INode inode = load.getResult();
			p.lastAccessTime = inode.lastAccessTime * 1000;
			p.lastModificationTime = inode.lastModificationTime * 1000;
			p.hardLinks = inode.hardLinks;
			p.uid = inode.uid;
			p.gid = inode.gid;
		}
		return p;
	}

}
