package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.filesystem.ext.ExtFS.INode;

public class ExtFile extends ExtFSEntry {

	ExtFile(ExtDirectory parent, long inodeIndex, String name) {
		super(parent, inodeIndex, name);
	}
	
	ExtFile(ExtDirectory parent, String name, INode inode) {
		super(parent, name, inode);
	}
	
}
