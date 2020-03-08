package net.lecousin.dataformat.filesystem.fat;

import java.io.IOException;
import java.util.ArrayList;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.ui.iconset.hardware.HardwareIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class FATDataFormat implements ContainerDataFormat {


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
	
	public static OpenedDataCache<FAT> cache = new OpenedDataCache<FAT>(FAT.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncSupplier<FAT, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncSupplier<FAT, Exception> result = new AsyncSupplier<>();
			IO.Readable.Seekable content;
			if (io instanceof IO.Readable.Seekable)
				content = (IO.Readable.Seekable)io;
			else
				try { content = new ReadableToSeekable(io, 65536); }
				catch (IOException e) {
					result.error(e);
					return result;
				}
			AsyncSupplier<FAT, IOException> open = FAT.open(content);
			open.onDone(() -> {
				open.getResult().getLoadedSynch().onDone(() -> {
					result.unblockSuccess(open.getResult());
				}, result, e -> e);
			}, result, e -> e);
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(FAT fs) {
			try { fs.close(); }
			catch (Exception e) {}
		}
		
	};

	@Override
	public AsyncSupplier<FATDataFormatInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<FATDataFormatInfo, Exception> result = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<FAT>, Exception> getCache = cache.open(data, this, priority, null, 0);
		getCache.thenStart("Get FAT File System information", priority, () -> {
			FAT fs = getCache.getResult().get();
			FATDataFormatInfo info = new FATDataFormatInfo();
			info.bytesPerSector = fs.bytesPerSector;
			info.sectorsPerCluster = fs.sectorsPerCluster;
			info.volumeLabel = fs.volumeLabel;
			info.serialNumber = fs.serialNumber;
			info.formatterSystem = fs.formatterSystem;
			result.unblockSuccess(info);
		}, result);
		return result;
	};
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading FAT File System");
		AsyncSupplier<CachedObject<FAT>, Exception> getCache = cache.open(container, this, Priority.IMPORTANT, progress, 300);
		Task.cpu("Get FAT File System root directory content", Priority.IMPORTANT, y -> {
			if (getCache.hasError()) {
				listener.error(getCache.getError());
				progress.error(getCache.getError());
				LCCore.getApplication().getDefaultLogger().error("Error reading FAT file system", getCache.getError());
				return null;
			}
			if (getCache.isCancelled()) {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
				return null;
			}
			FAT fs = getCache.getResult().get();
			listener.elementsReady(new ArrayList<>(0));
			MutableLong work = new MutableLong(700);
			fs.listRootEntries(new AsyncCollection.Listen<>(
				(elements) -> {
					ArrayList<Data> list = new ArrayList<>(elements.size());
					for (FatEntry entry : elements)
						list.add(new FatEntryData(container, fs, entry));
					listener.elementsAdded(list);
					progress.progress(work.get() / 3);
					work.set(work.get() - work.get() / 3);
				},
				() -> {
					progress.done();
					getCache.getResult().release(FATDataFormat.this);
				},
				(error) -> {
					listener.error(error);
					progress.error(error);
					getCache.getResult().release(FATDataFormat.this);
				}
			));
			return null;
		}).startOn(getCache, true);
		return progress;
	}

	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO Auto-generated method stub
		return null;
	}

	public static class FAT12DataFormat extends FATDataFormat {
		
		public static final FAT12DataFormat instance = new FAT12DataFormat();
		
		private FAT12DataFormat() {}

		@Override
		public ILocalizableString getName() {
			return new FixedLocalizedString("FAT-12");
		}
		
	}

	public static class FAT16DataFormat extends FATDataFormat {
		
		public static final FAT16DataFormat instance = new FAT16DataFormat();
		
		private FAT16DataFormat() {};

		@Override
		public ILocalizableString getName() {
			return new FixedLocalizedString("FAT-16");
		}
		
	}

	public static class FAT32DataFormat extends FATDataFormat {
		
		public static final FAT32DataFormat instance = new FAT32DataFormat();
		
		private FAT32DataFormat() {};

		@Override
		public ILocalizableString getName() {
			return new FixedLocalizedString("FAT-32");
		}
		
	}
	
}
