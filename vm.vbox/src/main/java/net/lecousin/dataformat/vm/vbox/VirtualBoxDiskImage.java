package net.lecousin.dataformat.vm.vbox;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.TaskManager;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.AsyncCloseable;
import net.lecousin.framework.util.ConcurrentCloseable;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.RunnableWithParameter;
import net.lecousin.framework.util.StringUtil;

public class VirtualBoxDiskImage implements AsyncCloseable<Exception>, Closeable {

	public VirtualBoxDiskImage(IO.Readable.Seekable vdi) {
		io = vdi;
	}
	
	private IO.Readable.Seekable io;
	private int verMaj;
	private int verMin;
	private ImageType imageType;
	private String comment;
	private long size;
	private long blockSize;
	private long nbBlocks;
	private long nbBlocksAllocated;
	private String uid;
	//private String uidLastModified;
	//private String uidPrimaryImage;
	private long blocksOffset;
	private long dataOffset;
	private byte[] table;

	public enum ImageType {
		DYNAMIC, STATIC, UNDO, DIFF;
		
		public static ImageType fromValue(int val) {
			switch (val) {
			case 1: return DYNAMIC;
			case 2: return STATIC;
			case 3: return UNDO;
			case 4: return DIFF;
			default: return null;
			}
		}
	}
	
	public int getMajorVersion() { return verMaj; }
	public int getMinorVersion() { return verMin; }
	public ImageType getImageType() { return imageType; }
	public String getComment() { return comment; }
	public long getSize() { return size; }
	public long getBlockSize() { return blockSize; }
	public long getNumberOfBlocks() { return nbBlocks; }
	public long getNumberOfAllocatedBlocks() { return nbBlocksAllocated; }
	public String getUID() { return uid; }
	
	public ISynchronizationPoint<Exception> open(WorkProgress progress, long work) {
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		byte[] buf1 = new byte[4];
		AsyncWork<Integer, IOException> read = io.readFullyAsync(0x44, ByteBuffer.wrap(buf1));
		sp.onCancel((event) -> { read.unblockCancel(event); });
		long stepVersion = work / 100;
		long stepHeader = work / 10;
		long stepTable = work - stepVersion - stepHeader;
		read.listenInlineSP(() -> {
			long ver = DataUtil.readUnsignedIntegerLittleEndian(buf1, 0);
			verMaj = (int)(ver >> 16);
			verMin = (int)(ver & 0xFFFF);
			if (verMaj > 1) {
				if (progress != null) progress.progress(work);
				sp.unblock();
				return;
			}
			byte[] buf;
			if (verMaj == 0)
				buf = new byte[348]; // version 0
			else if (verMin == 1)
				buf = new byte[384]; // version 1.1
			else
				buf = new byte[384+16]; // version 1.2
			
			if (progress != null) progress.progress(stepVersion);
			io.readFullyAsync(0x48, ByteBuffer.wrap(buf)).listenInlineSP(() -> {
				int pos = verMaj == 0 ? 0 : 4;
				imageType = ImageType.fromValue((int)DataUtil.readUnsignedIntegerLittleEndian(buf, pos));
				pos += 8;
				int i = 0;
				while (buf[pos + i] != 0 && i < 256) i++;
				comment = new String(buf, pos, i, StandardCharsets.UTF_8);
				pos += 256; // after comment
				if (verMaj == 1) {
					blocksOffset = DataUtil.readUnsignedIntegerLittleEndian(buf, pos);
					dataOffset = DataUtil.readUnsignedIntegerLittleEndian(buf, pos + 4);
					pos += 8;
				}
				pos += 16; // legacy geometry
				if (verMaj == 1)
					pos += 4; // was BIOS HDD translation mode, now unused
				size = DataUtil.readLongLittleEndian(buf, pos);
				blockSize = DataUtil.readUnsignedIntegerLittleEndian(buf, pos + 8);
				pos += 12;
				if (verMaj == 1) {
					// TODO ? extraBlockDataSize = DataUtil.readUnsignedIntegerLittleEndian(buf, pos);
					pos += 4;
				}
				nbBlocks = DataUtil.readUnsignedIntegerLittleEndian(buf, pos);
				nbBlocksAllocated = DataUtil.readUnsignedIntegerLittleEndian(buf, pos + 4);
				uid = StringUtil.encodeHexa(buf, pos + 8, 16);
				//uidLastModified = StringUtil.encodeHexa(buf, pos + 24, 16);
				//uidPrimaryImage = StringUtil.encodeHexa(buf, pos + 40, 16);
				pos += 56;
				if (verMaj == 0) {
					blocksOffset = 72 + 348;
					dataOffset = blocksOffset + nbBlocks * 4;
				}
				if (progress != null) progress.progress(stepHeader);
				table = new byte[(int)(nbBlocks*4)];
				io.readFullyAsync(blocksOffset, ByteBuffer.wrap(table)).listenInlineSP(() -> {
					if (progress != null) progress.progress(stepTable);
					sp.unblock();
				}, sp);
			}, sp);
		}, sp);
		return sp;
	}
	
	@Override
	public ISynchronizationPoint<Exception> closeAsync() {
		table = null;
		return io.closeAsync();
	}
	
	@Override
	public void close() throws IOException {
		try { closeAsync().blockThrow(0); }
		catch (CancelException e) { /* ignore. */ }
		catch (Exception e) { throw IO.error(e); }
	}
	
	public IO.Readable.Seekable createIO(byte priority) {
		return new ContentIO(priority);
	}
	
	public class ContentIO extends ConcurrentCloseable implements IO.Readable.Seekable {
		private ContentIO(byte priority) {
			this.priority = priority;
		}
		
		private byte priority;
		private long pos = 0;

		@Override
		public String getSourceDescription() {
			return io.getSourceDescription();
		}

		@Override
		public IO getWrappedIO() {
			return io;
		}

		@Override
		public void setPriority(byte priority) {
			this.priority = priority;
		}

		@Override
		public byte getPriority() { return priority; }

		@Override
		public TaskManager getTaskManager() {
			return io.getTaskManager();
		}

		@Override
		protected ISynchronizationPoint<?> closeUnderlyingResources() {
			return new SynchronizationPoint<>(true);
		}

		@Override
		protected void closeResources(SynchronizationPoint<Exception> ondone) {
			ondone.unblock();
		}

		@Override
		public ISynchronizationPoint<IOException> canStartReading() {
			return new SynchronizationPoint<>(true);
		}

		@Override
		public int readSync(ByteBuffer buffer) throws IOException {
			return readSync(pos, buffer);
		}

		@Override
		public AsyncWork<Integer, IOException> readAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
			return readAsync(pos, buffer, ondone);
		}

		@Override
		public int readFullySync(ByteBuffer buffer) throws IOException {
			return readFullySync(pos, buffer);
		}

		@Override
		public AsyncWork<Integer, IOException> readFullyAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
			return readFullyAsync(pos, buffer, ondone);
		}

		@Override
		public long skipSync(long n) {
			long prev = pos;
			pos += n;
			if (pos < 0) pos = 0;
			if (pos > size) pos = size;
			return pos - prev;
		}

		@Override
		public AsyncWork<Long, IOException> skipAsync(long n, RunnableWithParameter<Pair<Long, IOException>> ondone) {
			Long s = Long.valueOf(skipSync(n));
			if (ondone != null) ondone.run(new Pair<>(s, null));
			return new AsyncWork<>(s, null);
		}

		@Override
		public long seekSync(SeekType type, long move) {
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
		public AsyncWork<Long, IOException> seekAsync(SeekType type, long move, RunnableWithParameter<Pair<Long, IOException>> ondone) {
			Long s = Long.valueOf(seekSync(type, move));
			if (ondone != null) ondone.run(new Pair<>(s, null));
			return new AsyncWork<>(s, null);
		}

		@Override
		public long getPosition() { return pos; }

		private long getBlock(int index) {
			if ((index + 1) * 4 > table.length || index < 0) {
				LCCore.getApplication().getDefaultLogger().error("Invalid VDI Block index " + index + ": only " + (table.length / 4) + " blocks.");
				return 0xFFFFFFFFL; // like an empty block
			}
			return DataUtil.readUnsignedIntegerLittleEndian(table, index * 4);
		}
		
		@Override
		public int readSync(long pos, ByteBuffer buffer) throws IOException {
			if (pos >= size) return -1;
			if (!buffer.hasRemaining()) return 0;
			int nb;
			int block = (int)(pos / blockSize);
			long blockOffset = pos % blockSize;
			long l = blockSize - blockOffset;
			if (l > buffer.remaining()) l = buffer.remaining();
			long realBlock = getBlock(block);
			if (realBlock != 0xFFFFFFFFL) {
				realBlock *= blockSize;
				realBlock += dataOffset;
				nb = io.readSync(realBlock + blockOffset, buffer);
				if (nb < 0) nb = 0;
			} else {
				for (int i = 0; i < l; ++i) buffer.put((byte)0);
				nb = (int)l;
			}
			this.pos = pos + nb;
			return nb;
		}

		@Override
		public AsyncWork<Integer, IOException> readAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
			if (pos >= size) {
				if (ondone != null) ondone.run(new Pair<>(Integer.valueOf(-1), null));
				return new AsyncWork<>(Integer.valueOf(-1), null);
			}
			if (!buffer.hasRemaining()) {
				if (ondone != null) ondone.run(new Pair<>(Integer.valueOf(0), null));
				return new AsyncWork<>(Integer.valueOf(0), null);
			}
			int block = (int)(pos / blockSize);
			long blockOffset = pos % blockSize;
			long l = blockSize - blockOffset;
			if (l > buffer.remaining()) l = buffer.remaining();
			long realBlock = getBlock(block);
			if (realBlock != 0xFFFFFFFFL) {
				realBlock *= blockSize;
				realBlock += dataOffset;
				io.setPriority(priority);
				return io.readAsync(realBlock + blockOffset, buffer, (res) -> {
					if (res.getValue1() != null) {
						int nb = res.getValue1().intValue();
						if (nb < 0) nb = 0;
						ContentIO.this.pos = pos + nb;
					}
				});
			}
			AsyncWork<Integer, IOException> result = new AsyncWork<>();
			int len = (int)l;
			new Task.Cpu.FromRunnable("Read unused block from VDI", priority, () -> {
				for (int i = 0; i < len; ++i) buffer.put((byte)0);
				ContentIO.this.pos = pos + len;
				result.unblockSuccess(Integer.valueOf(len));
			}).start();
			return result;
		}

		@Override
		public int readFullySync(long pos, ByteBuffer buffer) throws IOException {
			if (pos >= size) return -1;
			if (!buffer.hasRemaining()) return 0;
			int total = 0;
			do {
				int block = (int)(pos / blockSize);
				long blockOffset = pos % blockSize;
				long l = blockSize - blockOffset;
				if (l > buffer.remaining()) l = buffer.remaining();
				long realBlock = getBlock(block);
				if (realBlock != 0xFFFFFFFFL) {
					realBlock *= blockSize;
					realBlock += dataOffset;
					int nb = io.readSync(realBlock + blockOffset, buffer);
					if (nb <= 0) break;
					total += nb;
				} else {
					for (int i = 0; i < l; ++i) buffer.put((byte)0);
					total += l;
				}
				pos += l;
			} while(buffer.hasRemaining());
			this.pos = pos;
			return total;
		}

		@Override
		public AsyncWork<Integer, IOException> readFullyAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
			return IOUtil.readFullyAsync(this, pos, buffer, ondone);
		}

	}
	
}
