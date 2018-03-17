package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class ExtFSDataFormat implements DataFormat.DataContainerHierarchy {

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
	public String getSubDataPathSeparator() { return "/"; }
	
	@Override
	public void getSubDirectories(Data data, Directory parent, AsyncCollection<Directory> list) {
		cache.open(data, this, Task.PRIORITY_NORMAL, null, 0).listenInline(
			(fs) -> {
				ExtFSDataDirectory p;
				if (parent == null) // root
					p = new ExtFSDataDirectory(data, fs.get().getRoot());
				else
					p = (ExtFSDataDirectory)parent;
				AsyncWork<List<ExtFSEntry>, IOException> entries = p.dir.getEntries();
				entries.listenInline(() -> {
					if (entries.isSuccessful()) {
						List<Directory> dirs = new ArrayList<>();
						for (ExtFSEntry entry : entries.getResult()) {
							if (entry instanceof ExtDirectory)
								dirs.add(new ExtFSDataDirectory(data, (ExtDirectory)entry));
						}
						list.newElements(dirs);
					}
					list.done();
					fs.release(ExtFSDataFormat.this);
				});
			},
			(error) -> { list.done(); },
			(cancel) -> { list.done(); }
		);
	}
	
	@Override
	public void getSubData(Data data, Directory parent, AsyncCollection<Data> list) {
		cache.open(data, this, Task.PRIORITY_NORMAL, null, 0).listenInline(
			(fs) -> {
				ExtFSDataDirectory p;
				if (parent == null) // root
					p = new ExtFSDataDirectory(data, fs.get().getRoot());
				else
					p = (ExtFSDataDirectory)parent;
				AsyncWork<List<ExtFSEntry>, IOException> entries = p.dir.getEntries();
				entries.listenInline(() -> {
					if (entries.isSuccessful()) {
						List<Data> files = new ArrayList<>();
						for (ExtFSEntry entry : entries.getResult()) {
							if (entry instanceof ExtFile)
								files.add(new ExtFSDataFile(data, (ExtFile)entry));
						}
						list.newElements(files);
					}
					list.done();
					fs.release(ExtFSDataFormat.this);
				});
			},
			(error) -> { list.done(); },
			(cancel) -> { list.done(); }
		);
	}
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		// TODO really list all files ?
		list.done();
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		p.size = Long.valueOf(((ExtFSDataFile)subData).getSize());
		return p;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
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
	public ISynchronizationPoint<? extends Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}
	
}
