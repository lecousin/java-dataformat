package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat.DataContainerHierarchy.Directory;

public class ExtFSDataDirectory implements Directory {

	ExtFSDataDirectory(Data container, ExtDirectory dir) {
		this.container = container;
		this.dir = dir;
	}
	
	protected Data container;
	protected ExtDirectory dir;
	
	@Override
	public String getName() { return dir.getName(); }

	@Override
	public Data getContainerData() { return container; }

}
