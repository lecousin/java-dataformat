package net.lecousin.dataformat.core.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.concurrent.tasks.drives.CreateDirectoryTask;
import net.lecousin.framework.concurrent.tasks.drives.RemoveDirectoryTask;
import net.lecousin.framework.concurrent.tasks.drives.RemoveFileTask;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.provider.IOProvider;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class FileSystemDirectoryFormat implements DataFormat.DataContainerFlat {

	public static final FileSystemDirectoryFormat instance = new FileSystemDirectoryFormat();
	
	private FileSystemDirectoryFormat() {}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat", "Directory");
	}

	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		return new AsyncWork<>(null, null);
	}

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/core/folder_", ".png", 16, 24, 32, 48, 64, 256);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }

	@Override
	public String[] getFileExtensions() {
		return new String[0];
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}

	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		// TODO Auto-generated method stub
		
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
		return true;
	}

	@Override
	public ISynchronizationPoint<Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		Task.OnFile<Void,Exception> task = new Task.OnFile<Void,Exception>(((FileData)data).file, "Rename file", priority) {
			@Override
			public Void run() throws Exception {
				File dest = new File(((FileData)data).file, newName);
				if (dest.exists())
					throw new FileAlreadyExistsException(dest.getAbsolutePath());
				if (!((FileData)subData).file.renameTo(dest))
					throw new IOException("Unable to rename file " + ((FileData)subData).file.getAbsolutePath() + " to " + newName);
				return null;
			}
		};
		task.start();
		return task.getOutput();
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return true;
	}

	@Override
	public ISynchronizationPoint<Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		// TODO progress
		JoinPoint<Exception> jp = new JoinPoint<>();
		for (Data d : subData) {
			FileData fd = (FileData)d;
			File f = fd.file;
			Task.OnFile<?,?> task;
			if (f.isDirectory())
				task = new RemoveDirectoryTask(f, null, 0, null, priority, false);
			else
				task = new RemoveFileTask(f, priority);
			task.start();
			jp.addToJoin(task);
		}
		jp.start();
		return jp;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return ((FileData)parent).isDirectory;
	}

	@Override
	public ISynchronizationPoint<Exception> addSubData(Data data, List<Pair<String, IOProvider.Readable>> subData, byte priority) {
		// TODO
		return null;
	}
	
	@SuppressWarnings("resource")
	@Override
	public AsyncWork<Pair<Data, IO.Writable>, IOException> createSubData(Data data, String name, byte priority) {
		File dir = ((FileData)data).file;
		File file = new File(dir, name);
		if (file.exists())
			return new AsyncWork<>(null, new FileAlreadyExistsException(file.getAbsolutePath()));
		FileIO.WriteOnly output = new FileIO.WriteOnly(file, priority);
		FileData fileData = FileData.get(file);
		return new AsyncWork<>(new Pair<>(fileData, output), null);
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return true;
	}
	
	@Override
	public ISynchronizationPoint<IOException> createDirectory(Data parent, String name, byte priority) {
		File dir = ((FileData)parent).file;
		dir = new File(dir, name);
		CreateDirectoryTask mkdir = new CreateDirectoryTask(dir, false, true, priority);
		mkdir.start();
		return mkdir.getOutput();
	}
	
}
