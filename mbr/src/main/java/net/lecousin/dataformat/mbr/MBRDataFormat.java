package net.lecousin.dataformat.mbr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.DiskPartitionsUtil;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class MBRDataFormat implements DataFormat.DataContainerFlat {

	public static final MBRDataFormat instance = new MBRDataFormat();
	
	private MBRDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Disk");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.mbr/images/drive_harddisk_", ".png", 16, 24, 32, 48, 64);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
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
						partition.data = new SubData(data, p.start, p.size, "Partition " + index++);
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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		cache.open(data, this, Task.PRIORITY_NORMAL, null, 0).listenInline(
			(res) -> {
				if (res == null) {
					list.done();
					return;
				}
				List<Data> l = new ArrayList<>();
				for (Partitions.Partition p : res.get().list)
					if (p.data != null)
						l.add(p.data);
				list.newElements(l);
				list.done();
				res.release(MBRDataFormat.this);
			},
			(error) -> { list.done(); },
			(cancel) -> { list.done(); }
		);
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
		p.size = Long.valueOf(((DiskPartition)subData.getProperty("Partition")).size);
		return p;
	}
}
