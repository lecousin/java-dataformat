package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
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
	
	public SynchronizationPoint<IOException> open() {
		SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
		byte[] buf = new byte[512];
		io.readFullyAsync(1024, ByteBuffer.wrap(buf)).listenAsync(
			new Task.Cpu.FromRunnable("Read Ext file system header", io.getPriority(), () -> {
				System.arraycopy(buf, 0x68, uuid, 0, 16);
				blockSize = 1 << (10 + DataUtil.readUnsignedIntegerLittleEndian(buf, 0x18));
				blocksPerGroup = DataUtil.readUnsignedIntegerLittleEndian(buf, 0x20);
				inodesPerGroup = DataUtil.readUnsignedIntegerLittleEndian(buf, 0x28);
				inodeSize = DataUtil.readUnsignedShortLittleEndian(buf, 0x58);
				is64bits = (buf[0x60] & 0x80) != 0;
				directoryEntriesRecordFiletype = (buf[0x60] & 0x02) != 0;
				if (is64bits)
					groupDescriptorSize = DataUtil.readUnsignedShortLittleEndian(buf, 0xFE);
				else
					groupDescriptorSize = 32;
				root = new ExtRootDirectory(ExtFS.this);
				sp.unblock();
			}),
			sp
		);
		return sp;
	}
	
	public void close() throws Exception {
		io.close();
		io = null;
	}
	
	AsyncWork<INode, IOException> readINode(long inodeIndex) {
		long group = (inodeIndex - 1) / inodesPerGroup;
		long index = (inodeIndex - 1) % inodesPerGroup;
		long offset = index * inodeSize;
		byte[] buf = new byte[(int)blockSize];
		AsyncWork<INode, IOException> result = new AsyncWork<>();
		io.readFullyAsync(blockSize + group * groupDescriptorSize, ByteBuffer.wrap(buf)).listenAsync(
			new Task.Cpu.FromRunnable("Read Ext FS INode", io.getPriority(), () -> {
				long inodeTableOffset = DataUtil.readUnsignedIntegerLittleEndian(buf, 0x08);
				if (groupDescriptorSize > 32 && is64bits)
					inodeTableOffset += DataUtil.readUnsignedIntegerLittleEndian(buf, 0x28) << 32;
				inodeTableOffset *= blockSize;
				io.readFullyAsync(inodeTableOffset + offset, ByteBuffer.wrap(buf, 0, inodeSize)).listenAsync(
					new Task.Cpu.FromRunnable("Read Ext FS INode", io.getPriority(), () -> {
						result.unblockSuccess(readINode(buf, 0));
					}),
					result
				);
			}),
			result
		);
		return result;
	}
	
	INode readINode(byte[] buf, int off) {
		INode inode = new INode();
		inode.mode = DataUtil.readUnsignedShortLittleEndian(buf, off+0x00);
		inode.uid = DataUtil.readUnsignedShortLittleEndian(buf, off+0x02) + DataUtil.readUnsignedShortLittleEndian(buf, off+0x78)<<16;
		inode.gid = DataUtil.readUnsignedShortLittleEndian(buf, off+0x18) + DataUtil.readUnsignedShortLittleEndian(buf, off+0x7A)<<16;
		inode.size = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x04);
		if (inode.isRegularFile())
			inode.size += DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x6C)<<32;
		inode.lastAccessTime = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x08);
		inode.lastInodeChangeTime = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x0C);
		inode.lastModificationTime = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x10);
		inode.deletionTime = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x14);
		inode.hardLinks = DataUtil.readUnsignedShortLittleEndian(buf, off+0x1A);
		inode.flags = DataUtil.readIntegerLittleEndian(buf, off+0x20);
		for (int i = 0; i < 15; ++i)
			inode.blocks[i] = DataUtil.readUnsignedIntegerLittleEndian(buf, off+0x28+i*4);
		return inode;
	}
	
}
