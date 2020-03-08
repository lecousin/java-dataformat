package net.lecousin.dataformat.archive.zip;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.hierarchy.DirectoryData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class ZipDataFormat extends ArchiveDataFormat {

	public static ZipDataFormat instance = new ZipDataFormat();
	
	protected ZipDataFormat() {
	}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("ZIP");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/archive/zip/zip_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "zip" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] { "application/zip" };
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
	public static OpenedDataCache<ZipArchive> cache = new OpenedDataCache<ZipArchive>(ZipArchive.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncSupplier<ZipArchive,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			ZipArchive zip = ZipArchive.loadForExtraction((IO.Readable.Seekable&IO.KnownSize)io);
			IAsync<IOException> sp = zip.getSynchOnReady();
			AsyncSupplier<ZipArchive,Exception> result = new AsyncSupplier<>();
			sp.onDone(new Runnable() {
				@Override
				public void run() {
					// TODO better progress
					if (progress != null) progress.progress(work);
					if (sp.hasError()) result.error(new Exception("Unable to read ZIP file " + io.getSourceDescription(), sp.getError()));
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(zip);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(ZipArchive zip) {
			try { zip.close(); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncSupplier<ZipDataFormatInfo,Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<ZipDataFormatInfo,Exception> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<ZipArchive>,Exception> get = cache.open(data, this, priority, null, 0);
		get.onDone(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				sp.unblockSuccess(createInfo(get.getResult().get()));
				get.getResult().release(ZipDataFormat.this);
			}
		});
		sp.onCancel(get::cancel);
		return sp;
	}
	
	private static ZipDataFormatInfo createInfo(ZipArchive zip) {
		ZipDataFormatInfo info = new ZipDataFormatInfo();
		info.nbFiles = new Long(zip.getZippedFiles().size());
		return info;
	}
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		Data zipData = container;
		while (zipData instanceof DirectoryData)
			zipData = ((DirectoryData)zipData).getContainer();
		WorkProgress progress = new WorkProgressImpl(1000, "Reading zip content");
		AsyncSupplier<CachedObject<ZipArchive>,Exception> getCache = cache.open(zipData, this, Priority.IMPORTANT, progress, 800);
		Data zip = zipData;
		Task.cpu("List zip content", Priority.IMPORTANT, t -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error opening ZIP file", getCache.getError());
				return null;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			listSubData(zip, container, listener, getCache.getResult().get(), progress);
			getCache.getResult().release(ZipDataFormat.this);
			return null;
		}).startOn(getCache, true);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
	private void listSubData(Data zipData, Data container, CollectionListener<Data> listener, ZipArchive zip, WorkProgress progress) {
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
		for (ZipArchive.ZippedFile f : zip.getZippedFiles()) {
			String name = f.filename;
			boolean dir = false;
			if (name.endsWith("/")) {
				// directory
				name = name.substring(0, name.length() - 1);
				dir = true;
			}
			int i = name.lastIndexOf('/');
			if (i < 0) {
				if (path.length() == 0) {
					if (dir) {
						if (!dirs.contains(name)) {
							content.add(new DirectoryData(container, this, new FixedLocalizedString(name)));
							dirs.add(name);
						}
					} else {
						content.add(new ZipFileData(zipData, f));
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
					content.add(new ZipFileData(zipData, f));
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
	public Class<ZipFileDataCommonProperties> getSubDataCommonProperties() {
		return ZipFileDataCommonProperties.class;
	}
	
	@Override
	public ZipFileDataCommonProperties getSubDataCommonProperties(Data subData) {
		if (subData instanceof ZipFileData) {
			ZipFileData zip = (ZipFileData)subData;
			ZipFileDataCommonProperties props = new ZipFileDataCommonProperties();
			if (zip.file.lastModificationTimestamp > 0)
				props.lastModificationTimestamp = new Long(zip.file.lastModificationTimestamp);
			if (zip.file.lastAccessTimestamp > 0)
				props.lastAccessTimestamp = new Long(zip.file.lastAccessTimestamp);
			if (zip.file.creationTimestamp > 0)
				props.creationTimestamp = new Long(zip.file.creationTimestamp);
			return props;
		}
		return null;
	}

}
