package net.lecousin.dataformat.image.io;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.io.IO;

public abstract class ImageReader {

	public ImageReader(AsyncWork<? extends ColorModel, Exception> colorModel) {
		this.colorModel = colorModel;
	}
	
	private AsyncWork<? extends ColorModel, Exception> colorModel;
	protected WritableRaster raster;

	protected abstract ISynchronizationPoint<Exception> readImageData(IO.ReadableByteStream io);
	
	public final AsyncWork<BufferedImage, Exception> read(IO.ReadableByteStream io) {
		ISynchronizationPoint<Exception> read = readImageData(io);
		AsyncWork<BufferedImage, Exception> result = new AsyncWork<>();
		CreateImage createImage = new CreateImage(io.getPriority());
		createImage.startOn(colorModel, true);
		JoinPoint.fromSynchronizationPoints(createImage.getOutput(), read).listenInline(new Runnable() {
			@Override
			public void run() {
				if (createImage.hasError()) { result.error(createImage.getError()); return; }
				if (createImage.isCancelled()) { result.cancel(createImage.getCancelEvent()); return; }
				if (read.hasError()) { result.error(read.getError()); return; }
				if (read.isCancelled()) { result.cancel(read.getCancelEvent()); return; }
				result.unblockSuccess(createImage.getResult());
			}
		});
		return result;
	}
	
	private final class CreateImage extends Task.Cpu<BufferedImage, Exception> {
		private CreateImage(byte priority) {
			super("Create image", priority);
		}
		@Override
		public BufferedImage run() throws Exception, CancelException {
			if (colorModel.hasError()) throw colorModel.getError();
			if (colorModel.isCancelled()) throw colorModel.getCancelEvent();
			return new BufferedImage(colorModel.getResult(), raster, false, null);
		}
	}
	
}
