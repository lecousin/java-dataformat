package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.concurrent.threads.TaskManager;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.util.ConcurrentCloseable;
import net.lecousin.framework.util.Pair;

public class FatEntryIO extends ConcurrentCloseable<IOException> implements IO.Readable.Seekable, IO.KnownSize {

	public FatEntryIO(FAT fat, FatEntry entry, Priority priority) {
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
	protected Priority priority;
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
	
	private AsyncSupplier<Long, IOException> getClusterAsync(int num) {
		byte[] buffer = new byte[4];
		return getClusterAsync(num, buffer);
	}

	private AsyncSupplier<Long, IOException> getClusterAsync(int num, byte[] buffer) {
		AsyncSupplier<Long, IOException> result = new AsyncSupplier<>();
		if (clusters[num - 1] != null)
			fat.getNextCluster(clusters[num - 1].longValue(), buffer).onDone((res) -> { clusters[num] = res; result.unblockSuccess(res); }, result);
		else
			getClusterAsync(num - 1, buffer).onDone((res) -> {
				fat.getNextCluster(res.longValue(), buffer).onDone((res2) -> { clusters[num] = res2; result.unblockSuccess(res2); }, result);
			}, result);
		return result;
	}
	
	@Override
	public IAsync<IOException> canStartReading() {
		return new Async<>(true);
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
	public Priority getPriority() {
		return priority;
	}
	@Override
	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	@Override
	public TaskManager getTaskManager() {
		return fat.io.getTaskManager();
	}

	@Override
	protected IAsync<IOException> closeUnderlyingResources() {
		return new Async<>(true);
	}

	@Override
	protected void closeResources(Async<IOException> ondone) {
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
	public AsyncSupplier<Integer, IOException> readAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		return readAsync(pos, buffer, ondone);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		if (pos >= entry.size) {
			if (ondone != null) ondone.accept(new Pair<>(Integer.valueOf(-1), null));
			return new AsyncSupplier<>(Integer.valueOf(-1), null);
		}
		int clusterNum = (int)(pos / clusterSize);
		long clusterOff = pos % clusterSize;
		AsyncSupplier<Integer, IOException> result = new AsyncSupplier<>();
		if (clusters[clusterNum] != null)
			readAsync(clusters[clusterNum].longValue(), clusterOff, pos, buffer, ondone, result);
		else
			IOUtil.listenOnDone(getClusterAsync(clusterNum), (res) -> { readAsync(res.longValue(), clusterOff, pos, buffer, ondone, result); }, result, ondone);
		return result;
	}
	
	private void readAsync(long cluster, long clusterOff, long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone, AsyncSupplier<Integer, IOException> result) {
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
			if (ondone != null) ondone.accept(new Pair<>(nb, null));
			result.unblockSuccess(nb);
		}, result, ondone);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readFullyAsync(ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
		return IOUtil.readFullyAsync(this, buffer, ondone);
	}

	@Override
	public AsyncSupplier<Integer, IOException> readFullyAsync(long pos, ByteBuffer buffer, Consumer<Pair<Integer, IOException>> ondone) {
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
	public AsyncSupplier<Long, IOException> skipAsync(long n, Consumer<Pair<Long, IOException>> ondone) {
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
	public AsyncSupplier<Long, IOException> seekAsync(SeekType type, long move, Consumer<Pair<Long, IOException>> ondone) {
		return IOUtil.seekAsyncUsingSync(this, type, move, ondone);
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
	public AsyncSupplier<Long, IOException> getSizeAsync() {
		return new AsyncSupplier<>(Long.valueOf(entry.size), null);
	}
	
}
