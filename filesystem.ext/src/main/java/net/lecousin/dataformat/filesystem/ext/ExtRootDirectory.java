package net.lecousin.dataformat.filesystem.ext;

public class ExtRootDirectory extends ExtDirectory {

	ExtRootDirectory(ExtFS fs) {
		super(null, 2, "");
		this.fs = fs;
	}
	
	protected ExtFS fs;
	
	@Override
	public ExtFS getFS() {
		return fs;
	}
	
}
