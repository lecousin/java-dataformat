package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableStringBuffer;

public class ExtFSData extends Data {

	ExtFSData(Data container, ExtFSEntry entry) {
		this.container = container;
		this.entry = entry;
		// launch inode loading
		entry.loadINode();
		// if directory, set the format
		if (entry instanceof ExtDirectory)
			setFormat(ExtFSDirectoryDataFormat.instance);
	}
	
	protected Data container;
	protected ExtFSEntry entry;
	
	@Override
	public FixedLocalizedString getName() { return new FixedLocalizedString(entry.getName()); }

	@Override
	public ILocalizableString getDescription() {
		return new LocalizableStringBuffer(container.getDescription(), "/" + entry.getName());
	}

	@Override
	public long getSize() {
		try { return entry.getSize(); }
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
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		return new AsyncSupplier<>(entry.openContent(priority), null);
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		// TODO
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		// TODO
		return null;
	}
	
}
