package net.lecousin.dataformat.vm.vbox;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class VDIDataFormat implements DataFormat.DataContainerFlat {

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
		protected AsyncWork<VirtualBoxDiskImage, Exception> open(IO.Readable io, WorkProgress progress, long work) {
			AsyncWork<VirtualBoxDiskImage, Exception> result = new AsyncWork<>();
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
			vdi.open(progress, work).listenInline(() -> { result.unblockSuccess(vdi); }, result);
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
	public AsyncWork<VDIDataFormatInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<VDIDataFormatInfo, Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<VirtualBoxDiskImage>, Exception> get = cache.open(data, this, priority, null, 0);
		get.listenAsync(new Task.Cpu.FromRunnable("Read VDI metadata", priority, () -> {
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
		}), sp);
		sp.onCancel((cancel) -> { get.cancel(cancel); });
		return sp;
	}

	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<VirtualBoxDiskImage>, Exception> get = cache.open(data, this, Task.PRIORITY_NORMAL, null, 0);
		get.listenInline(() -> {
			if (!get.isSuccessful()) {
				list.done();
				return;
			}
			list.newElements(Collections.singletonList(new VDIContentData(data, get.getResult().get())));
			list.done();
			get.getResult().release(VDIDataFormat.this);
		});
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		p.size = Long.valueOf(subData.getSize());
		return p;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		return null;
	}

	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}
	
}
