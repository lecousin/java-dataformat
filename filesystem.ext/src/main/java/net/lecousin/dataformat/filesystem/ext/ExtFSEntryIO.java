package net.lecousin.dataformat.filesystem.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import net.lecousin.dataformat.filesystem.ext.ExtFS.INode;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.concurrent.threads.TaskManager;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.util.ConcurrentCloseable;
import net.lecousin.framework.util.Pair;

public class ExtFSEntryIO extends ConcurrentCloseable<IOException> implements IO.Readable.Seekable, IO.KnownSize {

	static ExtFSEntryIO open(ExtFSEntry entry, Priority priority) {
		ExtFS fs = entry.getFS();
		IO.Readable.Seekable io = fs.io;
		//if (io instanceof IO.Writable.Seekable)
		//	return new Writable((IO.Readable.Seekable & IO.Writable.Seekable)io, entry, fs);
		return new ExtFSEntryIO(io, entry, fs, priority);
	}
	
	private ExtFSEntryIO(IO.Readable.Seekable io, ExtFSEntry entry, ExtFS fs, Priority priority) {
		this.io = io;
		this.fs = fs;
		this.entry = entry;
		this.priority = priority;
		loadINode = entry.loadINode();
	}
	
	protected IO.Readable.Seekable io;
	protected ExtFS fs;
	protected ExtFSEntry entry;
	protected long pos = 0;
	protected Priority priority;
	protected AsyncSupplier<INode, IOException> loadINode;
	
	@Override
	public IAsync<IOException> canStartReading() {
		return loadINode;
	}

	@Override
	public int readSync(long pos, ByteBuffer buffer) throws IOException {
		INode inode;
		try { inode = loadINode.blockResult(0); }
		catch (CancelException e) {
			return -1;
		}
		long block = pos / fs.blockSize;
		long blockOff = pos % fs.blockSize;
		if ((inode.blocks[0] & 0xFFFF) == 0xF30A && (inode.blocks[1] & 0xFFFF) == 4) {
			int nbEntries = (int)((inode.blocks[0] & 0xFFFF0000) >> 16);
			if (nbEntries >= 0 && nbEntries <= 4) {
				// ext4 extent tree
				int depth = (int)((inode.blocks[1] & 0xFFFF0000) >> 16);
				if (depth == 0) {
					for (int i = 0; i < nbEntries; ++i) {
						long firstBlock = inode.blocks[3+i*3];
						int nbBlocks = (int)(inode.blocks[4+i*3] & 0xFFFF);
						if (block >= firstBlock && block < firstBlock+nbBlocks) {
							long blockNum = inode.blocks[5+i*3] + ((inode.blocks[4+i*3] & 0xFFFF0000) << 16);
							long blockIndex = block-firstBlock;
							long maxLen = (nbBlocks-blockIndex)*fs.blockSize-blockOff;
							int limit = -1;
							if (buffer.remaining() > maxLen) {
								limit = buffer.limit();
								buffer.limit(buffer.position() + (int)maxLen);
							}
							int nb = io.readSync((blockNum+blockIndex)*fs.blockSize+blockOff, buffer);
							if (limit != -1)
								buffer.limit(limit);
							this.pos = pos;
							if (nb >= 0) this.pos += nb; 
							return nb;
						}
					}
					return -1;
				}
				long lastBlock = -1;
				long lastNum = -1;
				for (int i = 0; i < nbEntries; ++i) {
					long firstBlock = inode.blocks[3+i*3];
					long blockNum = inode.blocks[4+i*3] + ((inode.blocks[5+i*3] & 0xFFFF) << 32);
					if (lastBlock >= 0 && block >= lastBlock && block < firstBlock) {
						break;
					}
					lastBlock = firstBlock;
					lastNum = blockNum;
				}
				if (lastBlock == -1) return -1;
				return readFromExtentNodeSync(lastNum, block, blockOff, pos, buffer);
			}
		}
		int limit = -1;
		if (buffer.remaining() > fs.blockSize - blockOff) {
			limit = buffer.limit();
			buffer.limit(buffer.position() + (int)(fs.blockSize - blockOff));
		}
		int nb;
		if (block < 12) {
			nb = io.readSync(inode.blocks[(int)block]*fs.blockSize+blockOff, buffer);
		} else {
			block -= 12;
			if (block < fs.blockSize/4) {
				// 1 level
				byte[] buf = new byte[4];
				io.readFullySync(inode.blocks[12]*fs.blockSize+block*4, ByteBuffer.wrap(buf));
				long val = DataUtil.Read32U.LE.read(buf, 0);
				nb = io.readSync(val*fs.blockSize+blockOff, buffer);
			} else {
				block -= fs.blockSize/4;
				if (block < (fs.blockSize/4)*(fs.blockSize/4)) {
					// 2 levels
					int level2 = (int)(block/(fs.blockSize/4));
					int level2Off = (int)(block%(fs.blockSize/4));
					byte[] buf = new byte[4];
					io.readFullySync(inode.blocks[13]*fs.blockSize+level2*4, ByteBuffer.wrap(buf));
					long val = DataUtil.Read32U.LE.read(buf, 0);
					io.readFullySync(val*fs.blockSize+level2Off*4, ByteBuffer.wrap(buf));
					val = DataUtil.Read32U.LE.read(buf, 0);
					nb = io.readSync(val*fs.blockSize+blockOff, buffer);
				} else {
					block -= (fs.blockSize/4)*(fs.blockSize/4);
					// 3 levels
					int level2 = (int)(block/((fs.blockSize/4)*(fs.blockSize/4)));
					int level3 = (int)(block%((fs.blockSize/4)*(fs.blockSize/4)));
					int level3Off = (int)(block%(fs.blockSize/4));
					byte[] buf = new byte[4];
					io.readFullySync(inode.blocks[14]*fs.blockSize+level2*4, ByteBuffer.wrap(buf));
					long val = DataUtil.Read32U.LE.read(buf, 0);
					io.readFullySync(val*fs.blockSize+level3*4, ByteBuffer.wrap(buf));
					val = DataUtil.Read32U.LE.read(buf, 0);
					io.readFullySync(val*fs.blockSize+level3Off*4, ByteBuffer.wrap(buf));
					val = DataUtil.Read32U.LE.read(buf, 0);
					nb = io.readSync(val*fs.blockSize+blockOff, buffer);
				}
			}
		}
		if (limit != -1)
			buffer.limit(limit);
		this.pos = pos;
		if (nb >= 0) this.pos += nb; 
		return nb;
	}
	
	private int readFromExtentNodeSync(long nodeBlock, long block, long blockOff, long pos, ByteBuffer buffer) throws IOException {
		byte[] tmp = new byte[12];
		if (io.readFullySync(nodeBlock*fs.blockSize, ByteBuffer.wrap(tmp, 0, 8)) != 8) return -1;
		if (tmp[0] != 0x0A || (tmp[1] & 0xFF) != 0xF3)
			throw new IOException("Invalid ext4 extent node block: magic number missing in block "+nodeBlock);
		int nbEntries = DataUtil.Read16U.LE.read(tmp, 2);
		int depth = DataUtil.Read16U.LE.read(tmp, 6);
		if (depth == 0) {
			for (int i = 0; i < nbEntries; ++i) {
				if (io.readFullySync(nodeBlock*fs.blockSize+12+i*12, ByteBuffer.wrap(tmp)) != 12) return -1;
				long firstBlock = DataUtil.Read32U.LE.read(tmp, 0);
				int nbBlocks = DataUtil.Read16U.LE.read(tmp, 4);
				if (block >= firstBlock && block < firstBlock+nbBlocks) {
					long blockNum = DataUtil.Read32U.LE.read(tmp, 8) + (DataUtil.Read16U.LE.read(tmp, 6) << 32);
					long blockIndex = block-firstBlock;
					long maxLen = (nbBlocks-blockIndex)*fs.blockSize-blockOff; 
					int limit = -1;
					if (buffer.remaining() > maxLen) {
						limit = buffer.limit();
						buffer.limit(buffer.position() + (int)maxLen);
					}
					int nb = io.readSync((blockNum+blockIndex)*fs.blockSize+blockOff, buffer);
					if (limit != -1)
						buffer.limit(limit);
					this.pos = pos;
					if (nb >= 0) this.pos += nb; 
					return nb;
				}
			}
			return -1;
		}
		long lastBlock = -1;
		long lastNum = -1;
		for (int i = 0; i < nbEntries; ++i) {
			if (io.readFullySync(nodeBlock*fs.blockSize+12+i*12, ByteBuffer.wrap(tmp, 0, 10)) != 10) return -1;
			long firstBlock = DataUtil.Read32U.LE.read(tmp, 0);
			long blockNum = DataUtil.Read32U.LE.read(tmp, 4) + DataUtil.Read16U.LE.read(tmp, 8) << 32;
			if (lastBlock >= 0 && block >= lastBlock && block < firstBlock)
				break;
			lastBlock = firstBlock;
			lastNum = blockNum;
		}
		if (lastBlock == -1) return -1;
		return readFromExtentNodeSync(lastNum, block, blockOff, pos, buffer);
	}
	
	@Override
	public AsyncSupplier<Integer, IOException> readAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
		readAsync(pos, buffer, result, ondone);
		return result;
	}
	
	private void readAsync(long pos, ByteBuffer buffer, AsyncSupplier<Integer, IOException> result, Consumer<Pair<Integer, IOException>> ondone) {
		if (!loadINode.isDone()) {
			loadINode.thenStart("Read ExtFSEntry", getPriority(), () -> {
				readAsync(pos, buffer, result, ondone);
			}, true);
			return;
		}
		if (!loadINode.isSuccessful()) {
			if (loadINode.hasError())
				result.error(loadINode.getError());
			else
				result.cancel(loadINode.getCancelEvent());
			return;
		}
		INode inode = loadINode.getResult();

		long block = pos / fs.blockSize;
		long blockOff = pos % fs.blockSize;
		if ((inode.blocks[0] & 0xFFFF) == 0xF30A && (inode.blocks[1] & 0xFFFF) == 4) {
			int nbEntries = (int)((inode.blocks[0] & 0xFFFF0000) >> 16);
			if (nbEntries >= 0 && nbEntries <= 4) {
				// ext4 extent tree
				int depth = (int)((inode.blocks[1] & 0xFFFF0000) >> 16);
				if (depth == 0) {
					for (int i = 0; i < nbEntries; ++i) {
						long firstBlock = inode.blocks[3+i*3];
						int nbBlocks = (int)(inode.blocks[4+i*3] & 0xFFFF);
						if (block >= firstBlock && block < firstBlock+nbBlocks) {
							long blockNum = inode.blocks[5+i*3] + ((inode.blocks[4+i*3] & 0xFFFF0000) << 16);
							long blockIndex = block-firstBlock;
							long maxLen = (nbBlocks-blockIndex)*fs.blockSize-blockOff;
							int limit;
							if (buffer.remaining() > maxLen) {
								limit = buffer.limit();
								buffer.limit(buffer.position() + (int)maxLen);
							} else
								limit = -1;
							io.readAsync((blockNum+blockIndex)*fs.blockSize+blockOff, buffer, (res) -> {
								if (limit != -1)
									buffer.limit(limit);
								ExtFSEntryIO.this.pos = pos;
								if (res.getValue1() != null && res.getValue1().intValue() > 0) ExtFSEntryIO.this.pos += res.getValue1().intValue();
								if (ondone != null) ondone.accept(res);
								if (res.getValue1() != null) result.unblockSuccess(res.getValue1());
							}).onCancel(result::cancel);
							return;
						}
					}
					if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
					result.unblockSuccess(Integer.valueOf(-1));
					return;
				}
				long lastBlock = -1;
				long lastNum = -1;
				for (int i = 0; i < nbEntries; ++i) {
					long firstBlock = inode.blocks[3+i*3];
					long blockNum = inode.blocks[4+i*3] + ((inode.blocks[5+i*3] & 0xFFFF) << 32);
					if (lastBlock >= 0 && block >= lastBlock && block < firstBlock) {
						break;
					}
					lastBlock = firstBlock;
					lastNum = blockNum;
				}
				if (lastBlock == -1) {
					if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
					result.unblockSuccess(Integer.valueOf(-1));
					return;
				}
				readFromExtentNodeAsync(lastNum, block, blockOff, pos, buffer, result, ondone);
				return;
			}
		}
		int limit;
		if (buffer.remaining() > fs.blockSize - blockOff) {
			limit = buffer.limit();
			buffer.limit(buffer.position() + (int)(fs.blockSize - blockOff));
		} else
			limit = -1;
		Consumer<Pair<Integer, IOException>> myOndone = (res) -> {
			if (limit != -1)
				buffer.limit(limit);
			ExtFSEntryIO.this.pos = pos;
			if (res.getValue1() != null && res.getValue1().intValue() > 0) ExtFSEntryIO.this.pos += res.getValue1().intValue();
			if (ondone != null) ondone.accept(res);
			if (res.getValue1() != null) result.unblockSuccess(res.getValue1());
		};
		if (block < 12) {
			io.readAsync(inode.blocks[(int)block]*fs.blockSize+blockOff, buffer, myOndone).onCancel(result::cancel);
			return;
		}
		block -= 12;
		if (block < fs.blockSize/4) {
			// 1 level
			byte[] buf = new byte[4];
			io.readFullyAsync(inode.blocks[12]*fs.blockSize+block*4, ByteBuffer.wrap(buf)).onDone(
				() -> {
					long val = DataUtil.Read32U.LE.read(buf, 0);
					io.readAsync(val*fs.blockSize+blockOff, buffer, myOndone).onCancel(result::cancel);
				},
				result
			);
			return;
		}
		block -= fs.blockSize/4;
		if (block < (fs.blockSize/4)*(fs.blockSize/4)) {
			// 2 levels
			int level2 = (int)(block/(fs.blockSize/4));
			int level2Off = (int)(block%(fs.blockSize/4));
			byte[] buf = new byte[4];
			io.readFullyAsync(inode.blocks[13]*fs.blockSize+level2*4, ByteBuffer.wrap(buf)).onDone(
				() -> {
					long val = DataUtil.Read32U.LE.read(buf, 0);
					io.readFullyAsync(val*fs.blockSize+level2Off*4, ByteBuffer.wrap(buf)).onDone(
						() -> {
							long val2 = DataUtil.Read32U.LE.read(buf, 0);
							io.readAsync(val2*fs.blockSize+blockOff, buffer, myOndone).onCancel(result::cancel);
						},
						result
					);
				},
				result
			);
			return;
		}
		block -= (fs.blockSize/4)*(fs.blockSize/4);
		// 3 levels
		int level2 = (int)(block/((fs.blockSize/4)*(fs.blockSize/4)));
		int level3 = (int)(block%((fs.blockSize/4)*(fs.blockSize/4)));
		int level3Off = (int)(block%(fs.blockSize/4));
		byte[] buf = new byte[4];
		io.readFullyAsync(inode.blocks[14]*fs.blockSize+level2*4, ByteBuffer.wrap(buf)).onDone(
			() -> {
				long val = DataUtil.Read32U.LE.read(buf, 0);
				io.readFullyAsync(val*fs.blockSize+level3*4, ByteBuffer.wrap(buf)).onDone(
					() -> {
						long val2 = DataUtil.Read32U.LE.read(buf, 0);
						io.readFullyAsync(val2*fs.blockSize+level3Off*4, ByteBuffer.wrap(buf)).onDone(
							() -> {
								long val3 = DataUtil.Read32U.LE.read(buf, 0);
								io.readAsync(val3*fs.blockSize+blockOff, buffer, myOndone).onCancel(result::cancel);
							},
							result
						);
					},
					result
				);
			},
			result
		);
	}

	private void readFromExtentNodeAsync(long nodeBlock, long block, long blockOff, long pos, ByteBuffer buffer, AsyncSupplier<Integer, IOException> result, Consumer<Pair<Integer, IOException>> ondone) {
		byte[] tmp = new byte[12];
		io.readFullyAsync(nodeBlock*fs.blockSize, ByteBuffer.wrap(tmp, 0, 8)).onDone(
			(nb) -> {
				if (nb.intValue() != 8) {
					if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
					result.unblockSuccess(Integer.valueOf(-1));
					return;
				}
				if (tmp[0] != 0x0A || (tmp[1] & 0xFF) != 0xF3) {
					IOException err = new IOException("Invalid ext4 extent node block: magic number missing in block "+nodeBlock);
					if (ondone != null) ondone.accept(new Pair<>(null, err));
					result.error(err);
					return;
				}
				int nbEntries = DataUtil.Read16U.LE.read(tmp, 2);
				int depth = DataUtil.Read16U.LE.read(tmp, 6);
				if (depth == 0)
					readFromExtentNodeAsyncDepth0(0, nbEntries, tmp, nodeBlock, block, blockOff, pos, buffer, result, ondone);
				readFromExtentNodeAsyncDepth(-1, -1, 0, nbEntries, tmp, nodeBlock, block, blockOff, pos, buffer, result, ondone);
			},
			result
		);
	}

	private void readFromExtentNodeAsyncDepth0(int i, int nbEntries, byte[] tmp, long nodeBlock, long block, long blockOff, long pos, ByteBuffer buffer, AsyncSupplier<Integer, IOException> result, Consumer<Pair<Integer, IOException>> ondone) {
		if (i == nbEntries) {
			if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
			result.unblockSuccess(Integer.valueOf(-1));
			return;
		}
		io.readFullyAsync(nodeBlock*fs.blockSize+12+i*12, ByteBuffer.wrap(tmp)).onDone(
			(nb) -> {
				if (nb.intValue() != 12) {
					if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
					result.unblockSuccess(Integer.valueOf(-1));
					return;
				}
				long firstBlock = DataUtil.Read32U.LE.read(tmp, 0);
				int nbBlocks = DataUtil.Read16U.LE.read(tmp, 4);
				if (block >= firstBlock && block < firstBlock+nbBlocks) {
					long blockNum = DataUtil.Read32U.LE.read(tmp, 8) + (DataUtil.Read16U.LE.read(tmp, 6) << 32);
					long blockIndex = block-firstBlock;
					long maxLen = (nbBlocks-blockIndex)*fs.blockSize-blockOff; 
					int limit;
					if (buffer.remaining() > maxLen) {
						limit = buffer.limit();
						buffer.limit(buffer.position() + (int)maxLen);
					} else
						limit = -1;
					io.readAsync((blockNum+blockIndex)*fs.blockSize+blockOff, buffer, (res) -> {
						if (limit != -1)
							buffer.limit(limit);
						ExtFSEntryIO.this.pos = pos;
						if (res.getValue1() != null && res.getValue1().intValue() > 0) ExtFSEntryIO.this.pos += res.getValue1().intValue();
						if (ondone != null) ondone.accept(res);
						if (res.getValue1() != null) result.unblockSuccess(res.getValue1());
					}).onCancel(result::cancel);
					return;
				}
				readFromExtentNodeAsyncDepth0(i + 1, nbEntries, tmp, nodeBlock, block, blockOff, pos, buffer, result, ondone);
			},
			result
		);
	}
	
	private void readFromExtentNodeAsyncDepth(long lastBlock, long lastNum, int i, int nbEntries, byte[] tmp, long nodeBlock, long block, long blockOff, long pos, ByteBuffer buffer, AsyncSupplier<Integer, IOException> result, Consumer<Pair<Integer, IOException>> ondone) {
		if (i == nbEntries) {
			if (lastBlock == -1) {
				if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
				result.unblockSuccess(Integer.valueOf(-1));
			} else {
				readFromExtentNodeAsync(lastNum, block, blockOff, pos, buffer, result, ondone);
			}
			return;
		}
		io.readFullyAsync(nodeBlock*fs.blockSize+12+i*12, ByteBuffer.wrap(tmp, 0, 10)).onDone(
			(nb) -> {
				if (nb.intValue() != 10) {
					if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
					result.unblockSuccess(Integer.valueOf(-1));
					return;
				}
				long firstBlock = DataUtil.Read32U.LE.read(tmp, 0);
				long blockNum = DataUtil.Read32U.LE.read(tmp, 4) + DataUtil.Read16U.LE.read(tmp, 8) << 32;
				if (lastBlock >= 0 && block >= lastBlock && block < firstBlock) {
					readFromExtentNodeAsync(lastNum, block, blockOff, pos, buffer, result, ondone);
					return;
				}
				readFromExtentNodeAsyncDepth(firstBlock, blockNum, i + 1, nbEntries, tmp, nodeBlock, block, blockOff, pos, buffer, result, ondone);
			},
			result
		);
	}

	@Override
	public int readSync(ByteBuffer buffer) throws IOException {
		return readSync(pos, buffer);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		return readAsync(pos, buffer, ondone);
	}

	@Override
	public int readFullySync(ByteBuffer buffer) throws IOException {
		return readFullySync(pos, buffer);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readFullyAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		return readFullyAsync(pos, buffer, ondone);
	}

	@Override
	public long skipSync(long n) throws IOException {
		long p = pos;
		pos += n;
		if (pos < 0) pos = 0;
		long size = getSizeSync();
		if (pos > size) pos = size;
		return pos - p;
	}

	@Override
	public AsyncSupplier<Long, IOException> skipAsync(long n, Consumer<Pair<Long, IOException>> ondone) {
		AsyncSupplier<Long, IOException> result = new AsyncSupplier<>();
		getSizeAsync().onDone(
			(size) -> {
				long p = pos;
				pos += n;
				if (pos < 0) pos = 0;
				if (pos > size.longValue()) pos = size.longValue();
				result.unblockSuccess(Long.valueOf(pos - p));
			},
			result
		);
		return result;
	}

	@Override
	public long seekSync(SeekType type, long move) throws IOException {
		long size = getSizeSync();
		switch (type) {
		case FROM_BEGINNING: pos = move; break;
		case FROM_CURRENT: pos += move; break;
		case FROM_END: pos = size - move; break;
		}
		if (pos < 0) pos = 0;
		if (pos > size) pos = size;
		return pos;
	}

	@Override
	public AsyncSupplier<Long, IOException> seekAsync(SeekType type, long move, Consumer<Pair<Long, IOException>> ondone) {
		AsyncSupplier<Long, IOException> result = new AsyncSupplier<>();
		getSizeAsync().onDone(
			(size) -> {
				switch (type) {
				case FROM_BEGINNING: pos = move; break;
				case FROM_CURRENT: pos += move; break;
				case FROM_END: pos = size.longValue() - move; break;
				}
				if (pos < 0) pos = 0;
				if (pos > size.longValue()) pos = size.longValue();
				result.unblockSuccess(Long.valueOf(pos));
			},
			result
		);
		return result;
	}


	@Override
	public int readFullySync(long pos, ByteBuffer buffer) throws IOException {
		return IOUtil.readFullySync(this, pos, buffer);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readFullyAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		return IOUtil.readFullyAsync(this, pos, buffer, ondone);
	}

	@Override
	public long getPosition() {
		return pos;
	}
	
	@Override
	public long getSizeSync() throws IOException {
		try { return getSizeAsync().blockResult(0).longValue(); }
		catch (CancelException e) { return 0; }
	}
	
	@Override
	public AsyncSupplier<Long, IOException> getSizeAsync() {
		AsyncSupplier<Long, IOException> result = new AsyncSupplier<>();
		if (loadINode.isDone()) {
			if (!loadINode.isSuccessful()) {
				if (loadINode.hasError()) result.error(loadINode.getError());
				else result.cancel(loadINode.getCancelEvent());
			} else
				result.unblockSuccess(Long.valueOf(loadINode.getResult().size));
			return result;
		}
		loadINode.onDone(() -> { result.unblockSuccess(Long.valueOf(loadINode.getResult().size)); }, result);
		return result;
	}

	@Override
	public String getSourceDescription() {
		return entry.getName();
	}

	@Override
	public IO getWrappedIO() {
		return fs.io;
	}

	@Override
	public Priority getPriority() {
		return priority;
	}
	@Override
	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	@Override
	public TaskManager getTaskManager() {
		return io.getTaskManager();
	}

	@Override
	protected IAsync<IOException> closeUnderlyingResources() {
		loadINode.cancel(new CancelException("ExtFS entry closed"));
		return new Async<>(true);
	}

	@Override
	protected void closeResources(Async<IOException> ondone) {
		ondone.unblock();
	}

	/*
	public static class Writable extends ExtFSEntryIO implements IO.Writable.Seekable {
		
		private <T extends IO.Readable.Seekable & IO.Writable.Seekable> Writable(T io, ExtFSEntry entry, ExtFS fs) {
			super(io, entry, fs);
			this.io = io;
		}
		
		protected IO.Writable.Seekable io;
		
	}*/
	
}
