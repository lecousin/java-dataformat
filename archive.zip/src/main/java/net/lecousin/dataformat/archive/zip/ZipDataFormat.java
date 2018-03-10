package net.lecousin.dataformat.archive.zip;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class ZipDataFormat extends ArchiveDataFormat implements DataFormat.DataContainerHierarchy {

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
		protected AsyncWork<ZipArchive,Exception> open(IO.Readable io, WorkProgress progress, long work) {
			ZipArchive zip = ZipArchive.loadForExtraction((IO.Readable.Seekable&IO.KnownSize)io);
			ISynchronizationPoint<IOException> sp = zip.getSynchOnReady();
			AsyncWork<ZipArchive,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
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
	public AsyncWork<ZipDataFormatInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<ZipDataFormatInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<ZipArchive>,Exception> get = cache.open(data, this, priority, null, 0);
		get.listenInline(new Runnable() {
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
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				get.unblockCancel(event);
			}
		});
		return sp;
	}
	
	private static ZipDataFormatInfo createInfo(ZipArchive zip) {
		ZipDataFormatInfo info = new ZipDataFormatInfo();
		info.nbFiles = new Long(zip.getZippedFiles().size());
		return info;
	}
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<ZipArchive>,Exception> get = cache.open(data, this, Task.PRIORITY_IMPORTANT, null, 0);
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Loading Zip", Task.PRIORITY_IMPORTANT) {
			@Override
			public Void run() {
				if (!get.isSuccessful()) {
					list.done();
					LCCore.getApplication().getDefaultLogger().error("Error opening ZIP file", get.getError());
					return null;
				}
				CachedObject<ZipArchive> zip = get.getResult();
				try {
					Collection<ZipArchive.ZippedFile> files = zip.get().getZippedFiles();
					ArrayList<Data> dataList = new ArrayList<>(files.size());
					for (ZipArchive.ZippedFile f : files)
						dataList.add(new ZipFileData(data, f));
					list.newElements(dataList);
					list.done();
					return null;
				} finally {
					zip.release(ZipDataFormat.this);
				}
			}
		};
		task.startOn(get, true);
	}
	
	private static class ZipDirectory implements Directory {
		private ZipDirectory(String path, Data zip) {
			this.path = path;
			this.zip = zip;
		}
		private String path;
		private Data zip;
		@Override
		public String getName() {
			int i = path.lastIndexOf('/');
			if (i < 0) return path;
			return path.substring(i+1);
		}
		@Override
		public Data getContainerData() {
			return zip;
		}
	}
	
	@Override
	public void getSubDirectories(Data data, Directory parent, AsyncCollection<Directory> list) {
		getSubData(data, new AsyncCollection<Data>() {
			@Override
			public void newElements(Collection<Data> elements) {
				Set<String> names = new HashSet<String>();
				String p;
				if (parent == null) {
					// root directories
					p = "";
					for (Data file : elements) {
						String name = file.getName();
						int i = name.indexOf('/');
						if (i < 0) continue;
						names.add(name.substring(0,i));
					}
				} else {
					p = ((ZipDirectory)parent).path+'/';
					for (Data file : elements) {
						String name = file.getName();
						if (!name.startsWith(p)) continue;
						if (name.equals(p)) continue;
						int i = name.indexOf('/', p.length());
						if (i < 0) continue;
						names.add(name.substring(p.length(), i));
					}
				}
				ArrayList<Directory> dirs = new ArrayList<>(names.size());
				for (String name : names) dirs.add(new ZipDirectory(p+name, data));
				list.newElements(dirs);
			}
			@Override
			public void done() {
				list.done();
			}
			@Override
			public boolean isDone() {
				return list.isDone();
			}
		});
	}

	@Override
	public void getSubData(Data data, Directory parent, AsyncCollection<Data> list) {
		getSubData(data, new AsyncCollection<Data>() {
			@Override
			public void newElements(Collection<Data> elements) {
				ArrayList<Data> files = new ArrayList<>();
				String p = parent != null ? ((ZipDirectory)parent).path+'/' : null;
				for (Data file : elements) {
					String name = file.getName();
					if (p != null) {
						if (!name.startsWith(p)) continue;
						if (name.equals(p)) continue;
						int i = name.indexOf('/', p.length());
						if (i >= 0) continue;
						files.add(new ZipFileData.InDirectory((ZipFileData)file));
					} else {
						if (name.indexOf('/') >= 0) continue;
						files.add(new ZipFileData.InDirectory((ZipFileData)file));
					}
				}
				list.newElements(files);
			}
			@Override
			public void done() {
				list.done();
			}
			@Override
			public boolean isDone() {
				return list.isDone();
			}
		});
	}
	
	@Override
	public String getSubDataPathSeparator() {
		return "/";
	}
	
	@Override
	public Class<ZipFileDataCommonProperties> getSubDataCommonProperties() {
		return ZipFileDataCommonProperties.class;
	}
	
	@Override
	public ZipFileDataCommonProperties getSubDataCommonProperties(Data subData) {
		ZipFileDataCommonProperties props = new ZipFileDataCommonProperties();
		ZipFileData zip;
		if (subData instanceof ZipFileData)
			zip = (ZipFileData)subData;
		else
			zip = ((ZipFileData.InDirectory)subData).data;
		if (zip.file.lastModificationTimestamp > 0)
			props.lastModificationTimestamp = new Long(zip.file.lastModificationTimestamp);
		if (zip.file.lastAccessTimestamp > 0)
			props.lastAccessTimestamp = new Long(zip.file.lastAccessTimestamp);
		if (zip.file.creationTimestamp > 0)
			props.creationTimestamp = new Long(zip.file.creationTimestamp);
		return props;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		// TODO
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		// TODO
		return false;
	}
	
	@Override
	public ISynchronizationPoint<Exception> createDirectory(Data parent, String name, byte priority) {
		// TODO
		return null;
	}
}
