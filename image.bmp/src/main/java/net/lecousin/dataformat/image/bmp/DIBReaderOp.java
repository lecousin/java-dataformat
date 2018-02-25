package net.lecousin.dataformat.image.bmp;

import java.awt.image.BufferedImage;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.dataformat.image.bmp.io.DIBHeader;
import net.lecousin.dataformat.image.bmp.io.DIBReader;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
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
		
		<T extends IO.Readable.Buffered & IO.Readable.Seekable> AsyncWork<BufferedImage, Exception> provide(T io);
	}
	
	public static class DefaultProvider implements DIBImageProvider {
		@Override
		public <T extends IO.Readable.Buffered & IO.Readable.Seekable> AsyncWork<BufferedImage, Exception> provide(T io) {
			DIBHeader header = new DIBHeader();
			AsyncWork<Integer, Exception> readHeader = DIBReader.readHeader(io, header, -1);
			AsyncWork<BufferedImage, Exception> result = new AsyncWork<>();
			readHeader.listenAsync(new Task.Cpu<Void, NoException>("Read DIB image", io.getPriority()) {
				@Override
				public Void run() {
					if (readHeader.hasError()) { result.error(readHeader.getError()); return null; }
					if (readHeader.isCancelled()) { result.cancel(readHeader.getCancelEvent()); return null; }
					DIBReader.readBitmap(header, io).listenInline(result);
					return null;
				}
			}, true);
			return result;
		}
	}
	
	@Override
	public AsyncWork<Pair<BufferedImage,Object>,Exception> execute(Data data, Object params, byte priority, WorkProgress progress, long work) {
		AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.open(priority);
		DIBImageProvider provider = (DIBImageProvider)data.getProperty(DIBImageProvider.DATA_PROPERTY);
		if (provider == null)
			provider = new DefaultProvider();
		DIBImageProvider p = provider;
		AsyncWork<Pair<BufferedImage,Object>,Exception> result = new AsyncWork<>();
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				// TODO progress
				if (open.hasError()) { result.error(open.getError()); return; }
				if (open.isCancelled()) { result.cancel(open.getCancelEvent()); return; }
				AsyncWork<BufferedImage, Exception> img = p.provide((IO.Readable.Buffered & IO.Readable.Seekable)open.getResult());
				img.listenInline(new Runnable() {
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
