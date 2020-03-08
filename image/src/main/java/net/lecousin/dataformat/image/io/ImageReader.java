package net.lecousin.dataformat.image.io;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.async.JoinPoint;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;

public abstract class ImageReader {

	public ImageReader(AsyncSupplier<? extends ColorModel, Exception> colorModel) {
		this.colorModel = colorModel;
	}
	
	private AsyncSupplier<? extends ColorModel, Exception> colorModel;
	protected WritableRaster raster;

	protected abstract IAsync<Exception> readImageData(IO.ReadableByteStream io);
	
	public final AsyncSupplier<BufferedImage, Exception> read(IO.ReadableByteStream io) {
		IAsync<Exception> read = readImageData(io);
		AsyncSupplier<BufferedImage, Exception> result = new AsyncSupplier<>();
		Task<BufferedImage, Exception> createImage = Task.cpu("Create image", io.getPriority(), new CreateImage());
		createImage.startOn(colorModel, true);
		JoinPoint.from(createImage.getOutput(), read).onDone(new Runnable() {
			@Override
			public void run() {
				if (createImage.getOutput().hasError()) { result.error(createImage.getOutput().getError()); return; }
				if (createImage.isCancelled()) { result.cancel(createImage.getCancelEvent()); return; }
				if (read.hasError()) { result.error(read.getError()); return; }
				if (read.isCancelled()) { result.cancel(read.getCancelEvent()); return; }
				result.unblockSuccess(createImage.getOutput().getResult());
			}
		});
		return result;
	}
	
	private final class CreateImage implements Executable<BufferedImage, Exception> {
		@Override
		public BufferedImage execute(Task<BufferedImage, Exception> t) throws Exception, CancelException {
			if (colorModel.hasError()) throw colorModel.getError();
			if (colorModel.isCancelled()) throw colorModel.getCancelEvent();
			return new BufferedImage(colorModel.getResult(), raster, false, null);
		}
	}
	
}
