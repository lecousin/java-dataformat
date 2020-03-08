package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public class ExtFS {

	public ExtFS(IO.Readable.Seekable io) {
		this.io = io;
	}
	
	protected IO.Readable.Seekable io;
	protected byte[] uuid = new byte[16];
	protected long blockSize;
	protected long blocksPerGroup;
	protected long inodesPerGroup;
	protected int inodeSize;
	protected boolean is64bits;
	protected boolean directoryEntriesRecordFiletype;
	protected int groupDescriptorSize;
	protected ExtRootDirectory root;
	
	public ExtRootDirectory getRoot() {
		return root;
	}

	static class INode {
		public int mode;
		public int uid, gid;
		public long size;
		public long lastAccessTime;
		public long lastInodeChangeTime;
		public long lastModificationTime;
		public long deletionTime;
		public int hardLinks;
		public int flags;
		public long[] blocks = new long[15];
		
		public boolean isDirectory() { return (mode & 0x4000) != 0; }
		public boolean isRegularFile() { return (mode & 0x8000) != 0; }
	}
	
	public Async<IOException> open() {
		Async<IOException> sp = new Async<>();
		byte[] buf = new byte[512];
		io.readFullyAsync(1024, ByteBuffer.wrap(buf)).thenStart(
			Task.cpu("Read Ext file system header", io.getPriority(), t -> {
				System.arraycopy(buf, 0x68, uuid, 0, 16);
				blockSize = 1 << (10 + DataUtil.Read32U.LE.read(buf, 0x18));
				blocksPerGroup = DataUtil.Read32U.LE.read(buf, 0x20);
				inodesPerGroup = DataUtil.Read32U.LE.read(buf, 0x28);
				inodeSize = DataUtil.Read16U.LE.read(buf, 0x58);
				is64bits = (buf[0x60] & 0x80) != 0;
				directoryEntriesRecordFiletype = (buf[0x60] & 0x02) != 0;
				if (is64bits)
					groupDescriptorSize = DataUtil.Read16U.LE.read(buf, 0xFE);
				else
					groupDescriptorSize = 32;
				root = new ExtRootDirectory(ExtFS.this);
				sp.unblock();
				return null;
			}),
			sp
		);
		return sp;
	}
	
	public void close() throws Exception {
		io.close();
		io = null;
	}
	
	AsyncSupplier<INode, IOException> readINode(long inodeIndex) {
		long group = (inodeIndex - 1) / inodesPerGroup;
		long index = (inodeIndex - 1) % inodesPerGroup;
		long offset = index * inodeSize;
		byte[] buf = new byte[(int)blockSize];
		AsyncSupplier<INode, IOException> result = new AsyncSupplier<>();
		io.readFullyAsync(blockSize + group * groupDescriptorSize, ByteBuffer.wrap(buf)).thenStart(
			Task.cpu("Read Ext FS INode", io.getPriority(), t -> {
				long inodeTableOffset = DataUtil.Read32U.LE.read(buf, 0x08);
				if (groupDescriptorSize > 32 && is64bits)
					inodeTableOffset += DataUtil.Read32U.LE.read(buf, 0x28) << 32;
				inodeTableOffset *= blockSize;
				io.readFullyAsync(inodeTableOffset + offset, ByteBuffer.wrap(buf, 0, inodeSize)).thenStart(
					Task.cpu("Read Ext FS INode", io.getPriority(), t2 -> {
						result.unblockSuccess(readINode(buf, 0));
						return null;
					}),
					result
				);
				return null;
			}),
			result
		);
		return result;
	}
	
	INode readINode(byte[] buf, int off) {
		INode inode = new INode();
		inode.mode = DataUtil.Read16U.LE.read(buf, off+0x00);
		inode.uid = DataUtil.Read16U.LE.read(buf, off+0x02) + DataUtil.Read16U.LE.read(buf, off+0x78)<<16;
		inode.gid = DataUtil.Read16U.LE.read(buf, off+0x18) + DataUtil.Read16U.LE.read(buf, off+0x7A)<<16;
		inode.size = DataUtil.Read32U.LE.read(buf, off+0x04);
		if (inode.isRegularFile())
			inode.size += DataUtil.Read32U.LE.read(buf, off+0x6C)<<32;
		inode.lastAccessTime = DataUtil.Read32U.LE.read(buf, off+0x08);
		inode.lastInodeChangeTime = DataUtil.Read32U.LE.read(buf, off+0x0C);
		inode.lastModificationTime = DataUtil.Read32U.LE.read(buf, off+0x10);
		inode.deletionTime = DataUtil.Read32U.LE.read(buf, off+0x14);
		inode.hardLinks = DataUtil.Read16U.LE.read(buf, off+0x1A);
		inode.flags = DataUtil.Read32.LE.read(buf, off+0x20);
		for (int i = 0; i < 15; ++i)
			inode.blocks[i] = DataUtil.Read32U.LE.read(buf, off+0x28+i*4);
		return inode;
	}
	
	public AsyncSupplier<ExtDirectory, IOException> loadDirectory(List<String> path) {
		AsyncSupplier<ExtDirectory, IOException> result = new AsyncSupplier<>();
		loadDirectory(getRoot(), path, 0, result);
		return result;
	}
	
	private void loadDirectory(ExtDirectory parent, List<String> path, int pathIndex, AsyncSupplier<ExtDirectory, IOException> result) {
		parent.getEntries().onDone((entries) -> {
			String name = path.get(pathIndex);
			for (ExtFSEntry entry : entries) {
				if (!(entry instanceof ExtDirectory)) continue;
				if (!name.equals(entry.getName())) continue;
				if (pathIndex == path.size() - 1) {
					result.unblockSuccess((ExtDirectory)entry);
					return;
				}
				loadDirectory((ExtDirectory)entry, path, pathIndex + 1, result);
				return;
			}
			result.unblockError(new IOException("Directory does not exist"));
		}, result);
	}
	
}
