package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.filesystem.ext.ExtFS.INode;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.io.util.DataUtil;

public class ExtDirectory extends ExtFSEntry {

	ExtDirectory(ExtDirectory parent, long inodeIndex, String name) {
		super(parent, inodeIndex, name);
	}

	ExtDirectory(ExtDirectory parent, String name, INode inode) {
		super(parent, name, inode);
	}
	
	protected AsyncSupplier<List<ExtFSEntry>, IOException> entries = null;
	
	public AsyncSupplier<List<ExtFSEntry>, IOException> getEntries() {
		if (entries != null)
			return entries;
		entries = new AsyncSupplier<>();
		readContent();
		return entries;
	}
	
	private void readContent() {
		@SuppressWarnings("resource")
		ExtFSEntryIO content = openContent(getFS().io.getPriority());
		long pos = 0;
		byte[] buf = new byte[263];
		ExtFS fs = getFS();
		ArrayList<ExtFSEntry> list = new ArrayList<>();
		content.loadINode.onDone(
			(inode) -> { readContent(content, pos, buf, fs, inode, list); },
			entries
		);
		entries.onDone(() -> {
			try { content.close(); }
			catch (Exception e) {}
		});
	}
	
	private void readContent(ExtFSEntryIO content, long pos, byte[] buf, ExtFS fs, INode inode, List<ExtFSEntry> list) {
		if (pos >= inode.size - 8) {
			entries.unblockSuccess(list);
			return;
		}
		content.readFullyAsync(pos, ByteBuffer.wrap(buf, 0, 8)).onDone(
			() -> {
				long inodeNum = DataUtil.Read32U.LE.read(buf, 0);
				int recSize = DataUtil.Read16U.LE.read(buf, 4);
				if (recSize == 0) {
					entries.unblockSuccess(list);
					return;
				}
				if (inodeNum != 0) {
					int nameLen = buf[6] & 0xFF;
					int filetype;
					if (fs.directoryEntriesRecordFiletype)
						filetype = buf[7] & 0xFF;
					else
						filetype = -1;
					content.readFullyAsync(pos+8, ByteBuffer.wrap(buf, 0, nameLen)).onDone(
						() -> {
							String name = new String(buf, 0, nameLen);
							if (nameLen > 0 && !name.equals(".") && !name.equals("..")) { 
								if (filetype != -1) {
									if (filetype == 1) {
										list.add(new ExtFile(ExtDirectory.this, inodeNum, name));
									} else if (filetype == 2) {
										list.add(new ExtDirectory(ExtDirectory.this, inodeNum, name));
									} else {
										// TODO symbolic link?? ...
										// see http://www.nongnu.org/ext2-doc/ext2.html#I-MODE
									}
								} else {
									fs.readINode(inodeNum).onDone(
										(childInode) -> {
											if (childInode.isDirectory()) {
												list.add(new ExtDirectory(ExtDirectory.this, name, childInode));
											} else if (childInode.isRegularFile()) {
												list.add(new ExtFile(ExtDirectory.this, name, childInode));
											} else {
												// TODO symbolic link?? ...
												// see http://www.nongnu.org/ext2-doc/ext2.html#I-MODE
											}
											readContent(content, pos + recSize, buf, fs, inode, list);
										},
										entries
									);
									return;
								}
							}
							readContent(content, pos + recSize, buf, fs, inode, list);
						},
						entries
					);
					return;
				}
				readContent(content, pos + recSize, buf, fs, inode, list);
			},
			entries
		);
	}
	
}
