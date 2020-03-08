package net.lecousin.dataformat.archive.cab;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import net.lecousin.compression.mszip.MSZipReadable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.concurrent.threads.TaskManager;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.util.ConcurrentCloseable;
import net.lecousin.framework.util.Pair;

public class CabFolderIO {

	public static class Readable extends ConcurrentCloseable<IOException> implements IO.Readable.Seekable, MSZipReadable.BlockProvider {
		
		public Readable(IO.Readable.Seekable cabIO, long firstBlockOffset, int nbBlocks, int nbReservedBytesPerBlock) {
			this.io = cabIO;
			this.nbReservedBytesPerBlock = nbReservedBytesPerBlock;
			this.blocks = new Block[nbBlocks];
			readBlock(0, firstBlockOffset);
		}
		
		private IO.Readable.Seekable io;
		private int nbReservedBytesPerBlock;
		private Block[] blocks;
		private long pos = 0;
		private int blockIndex = 0;
		private long blockPos = 0;
		
		private static class Block {
			private long offset;
			private int size = -1;
			private Async<IOException> ready = new Async<>();
		}
		
		private void readBlock(int index, long offset) {
			byte[] buf = new byte[2];
			Block b = new Block();
			b.offset = offset + 8 + nbReservedBytesPerBlock;
			AsyncSupplier<Integer,IOException> read = io.readFullyAsync(offset + 4, ByteBuffer.wrap(buf));
			read.onDone(new Runnable() {
				@Override
				public void run() {
					if (read.hasError()) b.ready.error(read.getError());
					else if (read.isCancelled()) b.ready.cancel(read.getCancelEvent());
					else {
						b.size = DataUtil.Read16U.LE.read(buf, 0);
						b.ready.unblock();
					}
				}
			});
			blocks[index] = b;
		}
		
		@Override
		public String getSourceDescription() {
			return "CAB Folder in " + io.getSourceDescription();
		}

		@Override
		public IO getWrappedIO() {
			return io;
		}

		@Override
		public Priority getPriority() {
			return io.getPriority();
		}

		@Override
		public void setPriority(Priority priority) {
			io.setPriority(priority);
		}

		@Override
		public TaskManager getTaskManager() {
			return io.getTaskManager();
		}

		@Override
		public long getPosition() {
			return pos;
		}

		@Override
		protected IAsync<IOException> closeUnderlyingResources() {
			return null;
		}
		
		@Override
		protected void closeResources(Async<IOException> ondone) {
			io = null;
			blocks = null;
			ondone.unblock();
		}
		
		@Override
		public IAsync<IOException> canStartReading() {
			if (blockIndex == blocks.length) return new Async<>(true);
			return blocks[blockIndex].ready;
		}
		
		@Override
		public AsyncSupplier<ByteBuffer, IOException> readNextBlock() {
			if (blocks == null)
				return new AsyncSupplier<>(null, null, new CancelException("CabFolderIO closed"));
			if (blockIndex == blocks.length)
				return new AsyncSupplier<>(null,null);
			if (blockPos != 0)
				return new AsyncSupplier<>(null, new IOException("Current block has been already partially read"));
			if (blocks[blockIndex] == null) {
				Block prev = blocks[blockIndex-1];
				if (!prev.ready.isDone()) {
					AsyncSupplier<ByteBuffer, IOException> result = new AsyncSupplier<>();
					prev.ready.onDone(new Runnable() {
						@Override
						public void run() {
							if (prev.ready.hasError()) result.error(prev.ready.getError());
							else if (prev.ready.isCancelled()) result.cancel(prev.ready.getCancelEvent());
							else readNextBlock().forward(result);
						}
					});
					return operation(result);
				}
				readBlock(blockIndex, prev.offset + prev.size);
			}
			Block b = blocks[blockIndex];
			if (!b.ready.isDone()) {
				AsyncSupplier<ByteBuffer, IOException> result = new AsyncSupplier<>();
				b.ready.onDone(new Runnable() {
					@Override
					public void run() {
						if (b.ready.hasError()) { result.error(b.ready.getError()); }
						else if (b.ready.isCancelled()) { result.cancel(b.ready.getCancelEvent()); }
						else readNextBlock().forward(result);
					}
				});
				return operation(result);
			}
			AsyncSupplier<ByteBuffer, IOException> result = new AsyncSupplier<>();
			ByteBuffer buffer = ByteBuffer.allocate(b.size);
			io.readFullyAsync(b.offset, buffer, param -> {
				if (param.getValue1() != null) {
					pos += b.size;
					blockIndex++;
					if (blockIndex < blocks.length)
						readBlock(blockIndex, b.offset + b.size);
				}
				buffer.flip();
				if (param.getValue2() != null)
					result.error(param.getValue2());
				else
					result.unblockSuccess(buffer);
			});
			return operation(result);
		}

		@Override
		public AsyncSupplier<Integer, IOException> readAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
			if (blockIndex == blocks.length) {
				if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
				return new AsyncSupplier<>(Integer.valueOf(-1), null);
			}
			if (blocks[blockIndex] == null) {
				Block prev = blocks[blockIndex-1];
				if (!prev.ready.isDone()) {
					AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
					prev.ready.onDone(new Runnable() {
						@Override
						public void run() {
							if (prev.ready.hasError()) result.error(prev.ready.getError());
							else if (prev.ready.isCancelled()) result.cancel(prev.ready.getCancelEvent());
							else readAsync(buffer, ondone).forward(result);
						}
					});
					return operation(result);
				}
				readBlock(blockIndex, prev.offset + prev.size);
			}
			Block b = blocks[blockIndex];
			if (!b.ready.isDone()) {
				AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
				b.ready.onDone(new Runnable() {
					@Override
					public void run() {
						if (b.ready.hasError()) { if (ondone != null) ondone.accept(new Pair<>(null, b.ready.getError())); result.error(b.ready.getError()); }
						else if (b.ready.isCancelled()) { if (ondone != null) ondone.accept(new Pair<>(null,null)); result.cancel(b.ready.getCancelEvent()); }
						else readAsync(buffer, ondone).forward(result);
					}
				});
				return operation(result);
			}
			int limit = -1;
			if (buffer.remaining() > b.size - blockPos) {
				limit = buffer.limit();
				buffer.limit((int)(b.size - blockPos));
			}
			int lim = limit;
			AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
			io.readAsync(b.offset + blockPos, buffer, param -> {
				if (param.getValue1() != null) {
					int nb = param.getValue1().intValue();
					if (nb > 0) {
						blockPos += nb;
						pos += nb;
						if (blockPos == b.size) {
							blockPos = 0;
							blockIndex++;
							if (blockIndex < blocks.length)
								readBlock(blockIndex, b.offset + b.size);
						}
					}
				}
				if (lim != -1)
					buffer.limit(lim);
				if (ondone != null) ondone.accept(param);
				if (param.getValue2() != null)
					result.error(param.getValue2());
				else
					result.unblockSuccess(param.getValue1());
			});
			return operation(result);
		}
		
		@Override
		public int readSync(ByteBuffer buffer) throws IOException {
			return IOUtil.readSyncUsingAsync(this, buffer);
		}

		@Override
		public int readFullySync(ByteBuffer buffer) throws IOException {
			return IOUtil.readFullySyncUsingAsync(this, buffer);
		}

		@Override
		public AsyncSupplier<Integer, IOException> readFullyAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
			return operation(IOUtil.readFullyAsync(this, buffer, ondone));
		}

		@Override
		public AsyncSupplier<Integer, IOException> readAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
			return readAsync(pos, 0, buffer, ondone);
		}
		private AsyncSupplier<Integer, IOException> readAsync(long pos, int blockIndex, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
			if (blockIndex == blocks.length) {
				if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(0), null));
				return new AsyncSupplier<>(Integer.valueOf(0), null);
			}
			Block b = blocks[blockIndex];
			if (!b.ready.isDone()) {
				AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
				b.ready.onDone(new Runnable() {
					@Override
					public void run() {
						if (b.ready.hasError()) { if (ondone != null) ondone.accept(new Pair<>(null, b.ready.getError())); result.error(b.ready.getError()); }
						else if (b.ready.isCancelled()) { if (ondone != null) ondone.accept(new Pair<>(null,null)); result.cancel(b.ready.getCancelEvent()); }
						else readAsync(pos, blockIndex, buffer, ondone).forward(result);
					}
				});
				return operation(result);
			}
			if (pos < b.size) {
				int limit = -1;
				if (buffer.remaining() > b.size-pos) {
					limit = buffer.limit();
					buffer.limit((int)(b.size-pos));
				}
				int lim = limit;
				AsyncSupplier<Integer,IOException> result = new AsyncSupplier<>();
				io.readAsync(b.offset + pos, buffer, param -> {
					if (lim != -1) buffer.limit(lim);
					if (ondone != null) ondone.accept(param);
					if (param.getValue2() != null)
						result.error(param.getValue2());
					else
						result.unblockSuccess(param.getValue1());
				});
				return operation(result);
			}
			return readAsync(pos - b.size, blockIndex+1, buffer, ondone);
		}

		@Override
		public int readSync(long pos, ByteBuffer buffer) throws IOException {
			return IOUtil.readSyncUsingAsync(this, pos, buffer);
		}

		@Override
		public int readFullySync(long pos, ByteBuffer buffer) throws IOException {
			return IOUtil.readFullySyncUsingAsync(this, pos, buffer);
		}

		@Override
		public AsyncSupplier<Integer, IOException> readFullyAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
			return operation(IOUtil.readFullyAsync(this, pos, buffer, ondone));
		}

		@Override
		public AsyncSupplier<Long, IOException> seekAsync(SeekType type, long move, Consumer<Pair<Long, IOException>> ondone) {
			switch (type) {
			case FROM_BEGINNING:
				if (move < 0) move = 0;
				pos = 0;
				blockPos = 0;
				blockIndex = 0;
				return operation(goForward(move, ondone));
			case FROM_CURRENT:
				if (move == 0) {
					if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
					return new AsyncSupplier<>(Long.valueOf(pos), null);
				}
				if (move > 0)
					return operation(goForward(move, ondone));
				return operation(goBackward(-move, ondone));
			case FROM_END:
				AsyncSupplier<Long,IOException> result = new AsyncSupplier<>();
				long m = move;
				goForward(Long.MAX_VALUE, null).listen(new Listener<Long, IOException>() {
					@Override
					public void ready(Long r) {
						goBackward(m, ondone).forward(result);
					}
					@Override
					public void error(IOException error) {
						if (ondone != null) ondone.accept(new Pair<>(null,error));
						result.error(error);
					}

					@Override
					public void cancelled(CancelException event) {
						if (ondone != null) ondone.accept(new Pair<>(null,null));
						result.cancel(event);
					}
				});
				return operation(result);
			}
			if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
			return new AsyncSupplier<>(Long.valueOf(pos), null);
		}
		
		@Override
		public AsyncSupplier<Long, IOException> skipAsync(long n, Consumer<Pair<Long, IOException>> ondone) {
			AsyncSupplier<Long,IOException> move;
			long prevPos = pos;
			if (n > 0)
				move = goForward(n, ondone);
			else
				move = goBackward(-n, ondone);
			AsyncSupplier<Long,IOException> res = new AsyncSupplier<>();
			operation(move).onDone(new Runnable() {
				@Override
				public void run() {
					if (move.hasError())
						res.unblockError(move.getError());
					else
						res.unblockSuccess(Long.valueOf(pos - prevPos));
				}
			});
			return res;
		}
		
		private AsyncSupplier<Long, IOException> goForward(long move, Consumer<Pair<Long, IOException>> ondone) {
			if (blockIndex == blocks.length) {
				if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
				return new AsyncSupplier<>(Long.valueOf(pos), null);
			}
			Block b = blocks[blockIndex];
			if (!b.ready.isDone()) {
				AsyncSupplier<Long, IOException> result = new AsyncSupplier<>();
				b.ready.onDone(new Runnable() {
					@Override
					public void run() {
						if (b.ready.hasError()) { if (ondone != null) ondone.accept(new Pair<>(null, b.ready.getError())); result.error(b.ready.getError()); }
						else if (b.ready.isCancelled()) { if (ondone != null) ondone.accept(new Pair<>(null,null)); result.cancel(b.ready.getCancelEvent()); }
						else goForward(move, ondone).forward(result);
					}
				});
				return result;
			}
			if (blockPos + move < b.size) {
				pos += move;
				blockPos += move;
				if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
				return new AsyncSupplier<>(Long.valueOf(pos), null);
			}
			long rem = b.size - blockPos;
			pos += rem;
			blockIndex++;
			blockPos = 0;
			if (blockIndex < blocks.length && blocks[blockIndex] == null)
				readBlock(blockIndex, b.offset + b.size);
			return goForward(move - rem, ondone);
		}
		
		private AsyncSupplier<Long,IOException> goBackward(long move, Consumer<Pair<Long, IOException>> ondone) {
			if (move == 0) {
				if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
				return new AsyncSupplier<>(Long.valueOf(pos), null);
			}
			if (blockIndex == blocks.length) {
				Block b = blocks[blockIndex-1];
				if (!b.ready.isDone()) {
					AsyncSupplier<Long,IOException> result = new AsyncSupplier<>();
					long m = move;
					b.ready.onDone(new Runnable() {
						@Override
						public void run() {
							if (b.ready.hasError()) { if (ondone != null) ondone.accept(new Pair<>(null, b.ready.getError())); result.error(b.ready.getError()); }
							else if (b.ready.isCancelled()) { if (ondone != null) ondone.accept(new Pair<>(null,null)); result.cancel(b.ready.getCancelEvent()); }
							else goBackward(m, ondone).forward(result);
						}
					});
					return result;
				}
				blockPos = b.size;
			}
			if (move <= blockPos) {
				blockPos -= move;
				pos -= move;
				if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
				return new AsyncSupplier<>(Long.valueOf(pos), null);
			}
			move -= blockPos;
			pos -= blockPos;
			blockPos = 0;
			if (blockIndex == 0) {
				if (ondone != null) ondone.accept(new Pair<>(Long.valueOf(pos), null));
				return new AsyncSupplier<>(Long.valueOf(pos), null);
			}
			blockIndex--;
			blockPos = blocks[blockIndex].size;
			return goBackward(move, ondone);
		}

		@Override
		public long seekSync(SeekType type, long move) throws IOException {
			return IOUtil.seekSyncUsingAsync(this, type, move);
		}

		@Override
		public long skipSync(long n) throws IOException {
			return IOUtil.skipSyncUsingAsync(this, n);
		}

	}
	
}
