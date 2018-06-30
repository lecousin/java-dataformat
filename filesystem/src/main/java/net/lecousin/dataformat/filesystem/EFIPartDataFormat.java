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
import net.lecousin.framework.ui.iconset.hardware.HardwareIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class EFIPartDataFormat implements ContainerDataFormat {
	
	public static final EFIPartDataFormat instance = new EFIPartDataFormat();
	
	private EFIPartDataFormat() {
		
	}

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
	
	public static final String DATA_PROPERTY_PARTITION = "EFIPartition";

	public static OpenedDataCache<EFIPartitions> cache = new OpenedDataCache<EFIPartitions>(EFIPartitions.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<EFIPartitions, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncWork<EFIPartitions, Exception> result = new AsyncWork<>();
			IO.Readable.Seekable content;
			if (io instanceof IO.Readable.Seekable)
				content = (IO.Readable.Seekable)io;
			else
				try { content = new ReadableToSeekable(io, 65536); }
				catch (IOException e) {
					result.error(e);
					return result;
				}
			GPT gpt = new GPT();
			WorkProgress gptProgress = gpt.load(content, 0);
			WorkProgress.link(gptProgress, progress, work - work / 50);
			gptProgress.getSynch().listenAsync(new Task.Cpu.FromRunnable("Read EFI GPT Partitions", io.getPriority(), () -> {
				EFIPartitions partitions = new EFIPartitions();
				for (int i = 0; i < gpt.getPartitions().length; ++i) {
					GPT.PartitionEntry pe = gpt.getPartitions()[i];
					if (pe == null)
						continue;
					EFIPartitions.Partition p = new EFIPartitions.Partition();
					p.gpt = gpt;
					p.partitionIndex = i;
					p.data = new SubData(data, (pe.firstLBA - 1) * 512, (pe.lastLBA - pe.firstLBA + 1) * 512, new FixedLocalizedString(pe.name));
					p.data.setProperty(DATA_PROPERTY_PARTITION, p);
					partitions.list.add(p);
				}
				progress.progress(work / 50);
				result.unblockSuccess(partitions);
			}), result);
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(EFIPartitions partitions) {
		}
		
	};
	
	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		return null;
	}

	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading EFI partition table");
		cache.open(container, this, Task.PRIORITY_NORMAL, progress, 800).listenInline(
			(res) -> {
				if (res == null) {
					listener.elementsReady(new ArrayList<>(0));
					progress.done();
					return;
				}
				List<Data> l = new ArrayList<>();
				if (res.get() != null)
					for (EFIPartitions.Partition p : res.get().list)
						if (p.data != null)
							l.add(p.data);
				listener.elementsReady(l);
				progress.done();
				res.release(EFIPartDataFormat.this);
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
		return EFIPartitions.PartitionCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		EFIPartitions.PartitionCommonProperties p = new EFIPartitions.PartitionCommonProperties();
		EFIPartitions.Partition part = (EFIPartitions.Partition)subData.getProperty(DATA_PROPERTY_PARTITION);
		GPT.PartitionEntry pe = part.gpt.getPartitions()[part.partitionIndex];
		p.size = BigInteger.valueOf((pe.lastLBA - pe.firstLBA + 1) * 512);
		p.partitionType = pe.typeGUID;
		p.partitionGUID = pe.partitionGUID;
		return p;
	}

}
