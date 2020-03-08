package net.lecousin.dataformat.image.bmp;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.io.util.ReadableWithProgress;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class BMPReaderOp implements DataFormatReadOperation.OneToOne<BMPDataFormat, BufferedImage, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read BMP");
	}
	
	@Override
	public BMPDataFormat getInputFormat() { return BMPDataFormat.instance; }
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
	
	@Override
	public AsyncSupplier<Pair<BufferedImage,Object>,Exception> execute(Data data, Object params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<? extends IO.Readable.Seekable, IOException> open = data.openReadOnly(priority);
		Task<Pair<BufferedImage,Object>,Exception> task = Task.cpu("Reading BMP data", priority, taskContext -> {
			if (!open.isSuccessful())
				throw open.getError();
			if (progress != null) progress.progress(work/5);
			try (IO.Readable.Seekable io = open.getResult()) {
				return new Pair<>(ImageIO.read(IOAsInputStream.get(new ReadableWithProgress(io, data.getSize(), progress, work-work/5), true)), null);
			} catch (Throwable t) {
				throw new Exception("Error reading BMP data", t);
			} finally {
			}
		});
		open.thenStart(task, true);
		return task.getOutput();
	}
	
	@Override
	public void release(Data data, Pair<BufferedImage,Object> output) {
	}
	
}
