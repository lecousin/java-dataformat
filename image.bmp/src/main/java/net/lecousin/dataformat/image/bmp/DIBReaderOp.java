package net.lecousin.dataformat.image.bmp;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.dataformat.image.bmp.io.DIBHeader;
import net.lecousin.dataformat.image.bmp.io.DIBReader;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class DIBReaderOp implements DataFormatReadOperation.OneToOne<DIBDataFormat, BufferedImage, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read DIB");
	}
	
	@Override
	public DIBDataFormat getInputFormat() { return DIBDataFormat.instance; }
	@Override
	public Class<BufferedImage> getOutputType() { return BufferedImage.class; }
	
	@Override
	public ILocalizableString getOutputName() {
		return new LocalizableString("dataformat.image", "Image");
	}
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return ImageDataFormat.iconProvider;
	}
	
	@Override
	public Class<Object> getParametersClass() {
		return null;
	}
	@Override
	public Object createDefaultParameters() {
		return null;
	}
	
	public static interface DIBImageProvider {
		public static final String DATA_PROPERTY = "net.lecousin.dataformat.image.bmp.DIBImageProvider";
		
		<T extends IO.Readable.Buffered & IO.Readable.Seekable> AsyncSupplier<BufferedImage, Exception> provide(T io);
	}
	
	public static class DefaultProvider implements DIBImageProvider {
		@Override
		public <T extends IO.Readable.Buffered & IO.Readable.Seekable> AsyncSupplier<BufferedImage, Exception> provide(T io) {
			DIBHeader header = new DIBHeader();
			AsyncSupplier<Integer, Exception> readHeader = DIBReader.readHeader(io, header, -1);
			AsyncSupplier<BufferedImage, Exception> result = new AsyncSupplier<>();
			readHeader.thenStart("Read DIB image", io.getPriority(), (Task<Void, NoException> t) -> {
				if (readHeader.hasError()) { result.error(readHeader.getError()); return null; }
				if (readHeader.isCancelled()) { result.cancel(readHeader.getCancelEvent()); return null; }
				DIBReader.readBitmap(header, io).forward(result);
				return null;
			}, true);
			return result;
		}
	}
	
	@Override
	public AsyncSupplier<Pair<BufferedImage,Object>,Exception> execute(Data data, Object params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<? extends IO.Readable.Seekable, IOException> open = data.openReadOnly(priority);
		DIBImageProvider provider = (DIBImageProvider)data.getProperty(DIBImageProvider.DATA_PROPERTY);
		if (provider == null)
			provider = new DefaultProvider();
		DIBImageProvider p = provider;
		AsyncSupplier<Pair<BufferedImage,Object>,Exception> result = new AsyncSupplier<>();
		open.onDone(new Runnable() {
			@Override
			public void run() {
				// TODO progress
				if (open.hasError()) { result.error(open.getError()); return; }
				if (open.isCancelled()) { result.cancel(open.getCancelEvent()); return; }
				AsyncSupplier<BufferedImage, Exception> img = p.provide((IO.Readable.Buffered & IO.Readable.Seekable)open.getResult());
				img.onDone(new Runnable() {
					@Override
					public void run() {
						if (progress != null) progress.progress(work); // TODO
						if (img.hasError()) result.error(img.getError());
						else if (img.isCancelled()) result.cancel(img.getCancelEvent());
						else result.unblockSuccess(new Pair<>(img.getResult(), null));
						open.getResult().closeAsync();
					}
				});
			}
		});
		return result;
	}
	
	@Override
	public void release(Data data, Pair<BufferedImage,Object> output) {
	}
	
}
