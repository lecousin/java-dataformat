package net.lecousin.dataformat.vm.vbox;

import java.io.IOException;
import java.math.BigInteger;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataWrapperDataFormat;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class VDIDataFormat implements DataWrapperDataFormat {

	public static final VDIDataFormat instance = new VDIDataFormat();
	
	private VDIDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("VirtualBox Disk Image");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.vm.vbox/images/virtualbox-vdi-", "px.png", 16, 20, 24, 32, 40, 48, 64, 72, 80, 96, 128, 256, 512);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
	}

	public static final String[] extensions = new String[] { "vdi" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}
	
	public static OpenedDataCache<VirtualBoxDiskImage> cache = new OpenedDataCache<VirtualBoxDiskImage>(VirtualBoxDiskImage.class, 5*60*1000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncSupplier<VirtualBoxDiskImage, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncSupplier<VirtualBoxDiskImage, Exception> result = new AsyncSupplier<>();
			IO.Readable.Seekable content;
			if (io instanceof IO.Readable.Seekable)
				content = (IO.Readable.Seekable)io;
			else
				try { content = new ReadableToSeekable(io, 65536); }
				catch (IOException e) {
					result.error(e);
					return result;
				}
			VirtualBoxDiskImage vdi = new VirtualBoxDiskImage(content);
			vdi.open(progress, work).onDone(() -> { result.unblockSuccess(vdi); }, result, e -> e);
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return false;
		}

		@Override
		protected void close(VirtualBoxDiskImage vdi) {
			try { vdi.close(); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncSupplier<VDIDataFormatInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<VDIDataFormatInfo, Exception> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<VirtualBoxDiskImage>, Exception> get = cache.open(data, this, priority, null, 0);
		get.thenStart("Read VDI metadata", priority, () -> {
			@SuppressWarnings("resource")
			VirtualBoxDiskImage vdi = get.getResult().get();
			VDIDataFormatInfo info = new VDIDataFormatInfo();
			info.version = vdi.getMajorVersion() + "." + vdi.getMinorVersion();
			info.imageType = vdi.getImageType();
			info.comment = vdi.getComment();
			info.size = vdi.getSize();
			info.blockSize = vdi.getBlockSize();
			info.nbBlocks = vdi.getNumberOfBlocks();
			info.nbBlocksAllocated = vdi.getNumberOfAllocatedBlocks();
			info.uid = vdi.getUID();
			get.getResult().release(VDIDataFormat.this);
			sp.unblockSuccess(info);
		}, sp);
		sp.onCancel((cancel) -> { get.cancel(cancel); });
		return sp;
	}

	@Override
	public AsyncSupplier<Data, Exception> getWrappedData(Data container, WorkProgress progress, long work) {
		AsyncSupplier<Data, Exception> result = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<VirtualBoxDiskImage>, Exception> get = cache.open(container, this, Task.Priority.NORMAL, progress, work);
		get.onDone(() -> {
			result.unblockSuccess(new VDIContentData(container, get.getResult().get()));
			get.getResult().release(VDIDataFormat.this);
		}, result);
		return result;
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		p.size = BigInteger.valueOf(subData.getSize());
		return p;
	}
}
