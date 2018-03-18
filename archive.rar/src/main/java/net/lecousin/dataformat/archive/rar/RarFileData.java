package net.lecousin.dataformat.archive.rar;

import net.lecousin.dataformat.archive.rar.RarArchive.RARFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;

public class RarFileData extends Data {

	RarFileData(Data parent, RARFile file) {
		this.parent = parent;
		this.file = file;
	}
	
	Data parent;
	RARFile file;

	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public String getDescription() {
		return parent.getDescription()+'/'+file.getName();
	}

	@Override
	public long getSize() {
		return file.getUncompressedSize();
	}

	@Override
	public boolean hasContent() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public Data getContainer() {
		return parent;
	}

	@Override
	protected AsyncWork<IO.Readable, ? extends Exception> openIOReadOnly(byte priority) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		return null;
	}
	
}
