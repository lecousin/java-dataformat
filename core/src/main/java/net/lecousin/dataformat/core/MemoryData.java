package net.lecousin.dataformat.core;

import java.io.IOException;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.locale.ILocalizableString;

public class MemoryData extends Data {

	public MemoryData(Data parent, byte[] data, ILocalizableString name) {
		this.parent = parent;
		this.data = data;
		this.name = name;
	}
	
	protected Data parent;
	protected byte[] data;
	protected ILocalizableString name;
	
	@Override
	public ILocalizableString getName() {
		return name;
	}
	
	@Override
	public ILocalizableString getDescription() {
		return name;
	}
	
	@Override
	public long getSize() {
		return data.length;
	}
	
	@Override
	public boolean hasContent() {
		return true;
	}
	
	@Override
	public Data getContainer() {
		return parent;
	}
	
	@SuppressWarnings("resource")
	@Override
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		return new AsyncSupplier<>(new ByteArrayIO(data, "MemoryData"), null);
	}

	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}

	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		return null;
	}
	
}
