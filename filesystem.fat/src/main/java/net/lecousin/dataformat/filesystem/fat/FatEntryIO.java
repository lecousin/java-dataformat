package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.TaskManager;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.util.ConcurrentCloseable;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.RunnableWithParameter;

public class FatEntryIO extends ConcurrentCloseable implements IO.Readable.Seekable, IO.KnownSize {

	public FatEntryIO(FAT fat, FatEntry entry, byte priority) {
		this.fat = fat;
		this.entry = entry;
		this.priority = priority;
		clusterSize = fat.sectorsPerCluster * fat.bytesPerSector;
		int nbClusters = (int)(entry.size / clusterSize);
		if ((entry.size % clusterSize) != 0) nbClusters++;
		clusters = new Long[nbClusters];
		if (nbClusters > 0)
			clusters[0] = Long.valueOf(entry.cluster);
	}
	
	protected FAT fat;
	protected FatEntry entry;
	protected long pos = 0;
	protected byte priority;
	protected long clusterSize;
	protected Long[] clusters;
	
	private long getClusterSync(int num) throws IOException {
		if (clusters[num] != null)
			return clusters[num].longValue();
		byte[] buffer = new byte[4];
		long prev = getClusterSync(num - 1, buffer);
		try {
			return (clusters[num] = fat.getNextCluster(prev, buffer).blockResult(0)).longValue();
		} catch (CancelException e) {
			throw IO.error(e);
		}
	}

	private long getClusterSync(int num, byte[] buffer) throws IOException {
		if (clusters[num] != null)
			return clusters[num].longValue();
		long prev = getClusterSync(num - 1, buffer);
		try {
			return (clusters[num] = fat.getNextCluster(prev, buffer).blockResult(0)).longValue();
		} catch (CancelException e) {
			throw IO.error(e);
		}
	}
	
	private AsyncWork<Long, IOException> getClusterAsync(int num) {
		byte[] buffer = new byte[4];
		return getClusterAsync(num, buffer);
	}

	private AsyncWork<Long, IOException> getClusterAsync(int num, byte[] buffer) {
		AsyncWork<Long, IOException> result = new AsyncWork<>();
		if (clusters[num - 1] != null)
			fat.getNextCluster(clusters[num - 1].longValue(), buffer).listenInline((res) -> { clusters[num] = res; result.unblockSuccess(res); }, result);
		else
			getClusterAsync(num - 1, buffer).listenInline((res) -> {
				fat.getNextCluster(res.longValue(), buffer).listenInline((res2) -> { clusters[num] = res2; result.unblockSuccess(res2); }, result);
			}, result);
		return result;
	}
	
	@Override
	public ISynchronizationPoint<IOException> canStartReading() {
		return new SynchronizationPoint<>(true);
	}
	
	@Override
	public String getSourceDescription() {
		return entry.getName();
	}

	@Override
	public IO getWrappedIO() {
		return fat.io;
	}

	@Override
	public byte getPriority() {
		return priority;
	}
	@Override
	public void setPriority(byte priority) {
		this.priority = priority;
	}

	@Override
	public TaskManager getTaskManager() {
		return fat.io.getTaskManager();
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
	public int readSync(ByteBuffer buffer) throws IOException {
		return readSync(pos, buffer);
	}

	@Override
	public int readSync(long pos, ByteBuffer buffer) throws IOException {
		if (pos >= entry.size)
			return -1;
		int clusterNum = (int)(pos / clusterSize);
		long clusterOff = pos % clusterSize;
		long clusterAddr = fat.dataRegionAddress + (getClusterSync(clusterNum) - 2) * clusterSize;
		int len = buffer.remaining();
		if (pos + len > entry.size) len = (int)(entry.size - pos);
		if (len > clusterSize - clusterOff) len = (int)(clusterSize - clusterOff);
		int limit;
		if (len == buffer.remaining())
			limit = -1;
		else {
			limit = buffer.limit();
			buffer.limit(buffer.position() + len);
		}
		int nb = fat.io.readSync(clusterAddr + clusterOff, buffer);
		if (limit != -1)
			buffer.limit(limit);
		if (nb > 0)
			this.pos = pos + nb;
		return nb;
	}

	@Override
	public int readFullySync(ByteBuffer buffer) throws IOException {
		return IOUtil.readFully(this, buffer);
	}

	@Override
	public int readFullySync(long pos, ByteBuffer buffer) throws IOException {
		return IOUtil.readFullySync(this, pos, buffer);
	}

	@Override
	public AsyncWork<Integer, IOException> readAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
		return readAsync(pos, buffer, ondone);
	}

	@Override
	public AsyncWork<Integer, IOException> readAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
		if (pos >= entry.size) {
			if (ondone != null) ondone.run(new Pair<>(Integer.valueOf(-1), null));
			return new AsyncWork<>(Integer.valueOf(-1), null);
		}
		int clusterNum = (int)(pos / clusterSize);
		long clusterOff = pos % clusterSize;
		AsyncWork<Integer, IOException> result = new AsyncWork<>();
		if (clusters[clusterNum] != null)
			readAsync(clusters[clusterNum].longValue(), clusterOff, pos, buffer, ondone, result);
		else
			IOUtil.listenOnDone(getClusterAsync(clusterNum), (res) -> { readAsync(res.longValue(), clusterOff, pos, buffer, ondone, result); }, result, ondone);
		return result;
	}
	
	private void readAsync(long cluster, long clusterOff, long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone, AsyncWork<Integer, IOException> result) {
		long clusterAddr = fat.dataRegionAddress + (cluster - 2) * clusterSize;
		int len = buffer.remaining();
		if (pos + len > entry.size) len = (int)(entry.size - pos);
		if (len > clusterSize - clusterOff) len = (int)(clusterSize - clusterOff);
		int limit;
		if (len == buffer.remaining())
			limit = -1;
		else {
			limit = buffer.limit();
			buffer.limit(buffer.position() + len);
		}
		IOUtil.listenOnDone(fat.io.readAsync(clusterAddr + clusterOff, buffer), (nb) -> {
			if (limit != -1)
				buffer.limit(limit);
			if (nb > 0)
				this.pos = pos + nb;
			if (ondone != null) ondone.run(new Pair<>(nb, null));
			result.unblockSuccess(nb);
		}, result, ondone);
	}

	@Override
	public AsyncWork<Integer, IOException> readFullyAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
		return IOUtil.readFullyAsync(this, buffer, ondone);
	}

	@Override
	public AsyncWork<Integer, IOException> readFullyAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer, IOException>> ondone) {
		return IOUtil.readFullyAsync(this, pos, buffer, ondone);
	}

	@Override
	public long skipSync(long n) throws IOException {
		long prev = pos;
		pos += n;
		if (pos < 0) pos = 0;
		if (pos > entry.size) pos = entry.size;
		return pos - prev;
	}

	@Override
	public AsyncWork<Long, IOException> skipAsync(long n, RunnableWithParameter<Pair<Long, IOException>> ondone) {
		return IOUtil.skipAsyncUsingSync(this, n, ondone);
	}

	@Override
	public long seekSync(SeekType type, long move) {
		switch (type) {
		case FROM_BEGINNING: pos = move; break;
		case FROM_END: pos = entry.size - move; break;
		case FROM_CURRENT: pos += move; break;
		}
		if (pos < 0) pos = 0;
		if (pos > entry.size) pos = entry.size;
		return pos;
	}

	@Override
	public AsyncWork<Long, IOException> seekAsync(SeekType type, long move, RunnableWithParameter<Pair<Long, IOException>> ondone) {
		return IOUtil.seekAsyncUsingSync(this, type, move, ondone).getOutput();
	}

	@Override
	public long getPosition() {
		return pos;
	}

	@Override
	public long getSizeSync() {
		return entry.size;
	}

	@Override
	public AsyncWork<Long, IOException> getSizeAsync() {
		return new AsyncWork<>(Long.valueOf(entry.size), null);
	}
	
}
