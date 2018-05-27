package net.lecousin.dataformat.filesystem;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.DiskPartitionsUtil;
import net.lecousin.framework.ui.iconset.hardware.HardwareIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class MBRDataFormat implements ContainerDataFormat {

	public static final MBRDataFormat instance = new MBRDataFormat();
	
	private MBRDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Disk");
	}
	
	@Override
	public IconProvider getIconProvider() {
		return HardwareIconSet.Icons.HARD_DISK_INTERNAL.get();
	}

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}
	
	@Override
	public AsyncWork<DataFormatInfo, Exception> getInfo(Data data, byte priority) {
		return null;
	}

	public static OpenedDataCache<Partitions> cache = new OpenedDataCache<Partitions>(Partitions.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<Partitions, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncWork<Partitions, Exception> result = new AsyncWork<>();
			IO.Readable.Seekable content;
			if (io instanceof IO.Readable.Seekable)
				content = (IO.Readable.Seekable)io;
			else
				try { content = new ReadableToSeekable(io, 65536); }
				catch (IOException e) {
					result.error(e);
					return result;
				}
			new Task.Cpu.FromRunnable("Read partition table", Task.PRIORITY_NORMAL, () -> {
				List<DiskPartition> list = new ArrayList<>();
				if (!DiskPartitionsUtil.readPartitionTable(content, list)) {
					if (progress != null) progress.progress(work);
					result.unblockSuccess(null);
					return;
				}
				if (progress != null) progress.progress(work);
				Partitions partitions = new Partitions();
				int index = 1;
				for (DiskPartition p : list) {
					Partitions.Partition partition = new Partitions.Partition();
					partition.partitionInfo = p;
					if (p.start > 0) {
						partition.data = new SubData(data, p.start, p.size, new FixedLocalizedString("Partition " + index++));
						partition.data.setProperty("Partition", p);
					}
					partitions.list.add(partition);
				}
				result.unblockSuccess(partitions);
			}).start();
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(Partitions vdi) {
		}
		
	};
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading partition table");
		cache.open(container, this, Task.PRIORITY_NORMAL, progress, 800).listenInline(
			(res) -> {
				if (res == null) {
					listener.elementsReady(new ArrayList<>(0));
					progress.done();
					return;
				}
				List<Data> l = new ArrayList<>();
				if (res.get() != null)
					for (Partitions.Partition p : res.get().list)
						if (p.data != null)
							l.add(p.data);
				listener.elementsReady(l);
				progress.done();
				res.release(MBRDataFormat.this);
			},
			(error) -> {
				listener.error(error);
				progress.error(error);
			},
			(cancel) -> {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
			}
		);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		// TODO
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO Auto-generated method stub
		DataCommonProperties p = new DataCommonProperties();
		p.size = BigInteger.valueOf(((DiskPartition)subData.getProperty("Partition")).size);
		return p;
	}
}
