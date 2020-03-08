package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;

import net.lecousin.dataformat.filesystem.ext.ExtFS.INode;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;

public abstract class ExtFSEntry {

	protected ExtFSEntry(ExtDirectory parent, long inodeIndex, String name) {
		this.parent = parent;
		this.inodeIndex = inodeIndex;
		this.name = name;
	}

	protected ExtFSEntry(ExtDirectory parent, String name, INode inode) {
		this.parent = parent;
		this.loadINode = new AsyncSupplier<>(inode, null);
		this.name = name;
	}
	
	protected ExtDirectory parent;
	protected long inodeIndex;
	protected String name;
	protected AsyncSupplier<INode, IOException> loadINode = null;
	
	public AsyncSupplier<INode, IOException> loadINode() {
		if (loadINode == null)
			loadINode = getFS().readINode(inodeIndex);
		return loadINode;
	}

	public ExtDirectory getParent() {
		return parent;
	}
	
	public ExtFS getFS() {
		return parent.getFS();
	}
	
	public String getName() {
		return name;
	}

	public long getSize() throws Exception {
		INode inode = loadINode().blockResult(0);
		return inode.size;
	}
	
	public ExtFSEntryIO openContent(Priority priority) {
		return ExtFSEntryIO.open(this, priority);
	}
	
}
