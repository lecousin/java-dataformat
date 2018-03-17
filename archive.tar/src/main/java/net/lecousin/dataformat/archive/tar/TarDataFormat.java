package net.lecousin.dataformat.archive.tar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.lecousin.dataformat.archive.ArchiveDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
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
import net.lecousin.framework.util.Pair;

public class TarDataFormat extends ArchiveDataFormat implements DataFormat.DataContainerHierarchy {

	public static final TarDataFormat instance = new TarDataFormat();
	
	private TarDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("TAR");
	}
	
	public static final String[] extensions = { "tar" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	public static final String[] mimes = { "application/x-tar" };
	
	@Override
	public String[] getMIMETypes() {
		return mimes;
	}
	
	public static OpenedDataCache<TarFile> cache = new OpenedDataCache<TarFile>(TarFile.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<TarFile,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			TarFile tar = new TarFile((IO.Readable.Seekable)io, progress, work);
			ISynchronizationPoint<IOException> sp = tar.getSynchOnReady();
			AsyncWork<TarFile,Exception> result = new AsyncWork<>();
			sp.listenInline(new Runnable() {
				@Override
				public void run() {
					if (sp.hasError()) result.error(sp.getError());
					else if (sp.isCancelled()) result.cancel(sp.getCancelEvent());
					else result.unblockSuccess(tar);
				}
			});
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(TarFile tar) {
			try { tar.close(); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncWork<TarDataFormatInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<TarDataFormatInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<TarFile>,Exception> get = cache.open(data, this, priority, null, 0);
		get.listenInline(new Runnable() {
			@Override
			public void run() {
				if (get.isCancelled()) return;
				if (!get.isSuccessful()) {
					sp.unblockError(get.getError());
					return;
				}
				sp.unblockSuccess(createInfo(get.getResult().get()));
				get.getResult().release(TarDataFormat.this);
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
	
	private static TarDataFormatInfo createInfo(TarFile tar) {
		TarDataFormatInfo info = new TarDataFormatInfo();
		info.nbFiles = new Long(tar.getEntries().size());
		return info;
	}

	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<TarFile>,Exception> get = cache.open(data, this, Task.PRIORITY_IMPORTANT, null, 0);
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Loading Tar", Task.PRIORITY_IMPORTANT) {
			@Override
			public Void run() {
				if (!get.isSuccessful()) {
					list.done();
					getApplication().getDefaultLogger().error("Error opening TAR file", get.getError());
					return null;
				}
				CachedObject<TarFile> tar = get.getResult();
				try {
					Collection<TarEntry> files = tar.get().getEntries();
					ArrayList<Data> dataList = new ArrayList<>(files.size());
					for (TarEntry f : files) {
						String p = f.getPath();
						int i = p.lastIndexOf('/');
						if (i < 0) p = null;
						else p = p.substring(0, i);
						dataList.add(new SubData(data, f.getPosition(), f.getDataSize(), f.getName(), p));
					}
					list.newElements(dataList);
					list.done();
					return null;
				} finally {
					tar.release(TarDataFormat.this);
				}
			}
		};
		task.startOn(get, true);
	}
	
	private static class TarDirectory implements Directory {
		private TarDirectory(String path, Data tar) {
			this.path = path;
			this.tar = tar;
		}
		private String path;
		private Data tar;
		@Override
		public String getName() {
			int i = path.lastIndexOf('/');
			if (i < 0) return path;
			return path.substring(i+1);
		}
		@Override
		public Data getContainerData() {
			return tar;
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
						String path = ((SubData)file).getDirectoryPath();
						if (path == null) continue;
						int i = path.indexOf('/');
						if (i < 0) names.add(path);
						else names.add(path.substring(0, i));
					}
				} else {
					p = ((TarDirectory)parent).path+'/';
					for (Data file : elements) {
						String path = ((SubData)file).getDirectoryPath();
						if (path == null) continue;
						if (!path.startsWith(p)) continue;
						if (path.equals(p)) continue;
						int i = path.indexOf('/', p.length());
						if (i < 0) names.add(path.substring(p.length()));
						else names.add(path.substring(p.length(), i));
					}
				}
				ArrayList<Directory> dirs = new ArrayList<>(names.size());
				for (String name : names) dirs.add(new TarDirectory(p+name, data));
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
				String p = parent != null ? ((TarDirectory)parent).path : null;
				for (Data file : elements) {
					String path = ((SubData)file).getDirectoryPath();
					if (p != null) {
						if (path == null) continue;
						if (!path.equals(p)) continue;
						files.add(file);
					} else {
						if (path != null) continue;
						files.add(file);
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
	public Class<DataCommonProperties> getSubDataCommonProperties() {
		return null; // TODO
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
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
