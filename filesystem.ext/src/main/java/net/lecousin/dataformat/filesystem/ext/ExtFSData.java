package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		return new AsyncWork<>(entry.openContent(priority), null);
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		// TODO
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		// TODO
		return null;
	}
	
}
