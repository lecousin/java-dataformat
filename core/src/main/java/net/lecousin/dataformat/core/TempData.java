package net.lecousin.dataformat.core;

import java.io.IOException;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.SubIO;

public class TempData extends Data {

	public <T extends IO.Readable.Seekable&IO.KnownSize> TempData(String name, T io) throws IOException {
		this.name = name;
		this.io = io;
		this.size = io.getSizeSync();
	}
	
	private String name;
	private IO.Readable.Seekable io;
	private long size;
	
	@Override
	public String getName() { return name; }

	@Override
	public String getDescription() { return name; }

	@Override
	public long getSize() { return size; }

	@Override
	public boolean hasContent() {
		return true;
	}
	@Override
	public Data getContainer() {
		return null;
	}

	@SuppressWarnings("resource")
	@Override
	protected AsyncWork<IO, ? extends Exception> openIO(byte priority) {
		return new AsyncWork<IO,Exception>(new SubIO.Readable.Seekable(io, 0, size, name, false),null);
	}
	
}
