package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;

public class ExtFSDataFile extends Data {

	ExtFSDataFile(Data container, ExtFile file) {
		this.container = container;
		this.file = file;
	}
	
	protected Data container;
	protected ExtFile file;
	
	@Override
	public String getName() { return file.getName(); }

	@Override
	public String getDescription() { return file.getName(); }

	@Override
	public long getSize() {
		try { return file.getSize(); }
		catch (Exception e) {
			LCCore.getApplication().getDefaultLogger().error("Error reading ExtFS inode", e);
			return 0;
		}
	}

	@Override
	public boolean hasContent() { return true; }

	@Override
	public Data getContainer() { return container; }

	@Override
	protected AsyncWork<IO, ? extends Exception> openIO(byte priority) {
		return new AsyncWork<>(file.openContent(priority), null);
	}
	
}
