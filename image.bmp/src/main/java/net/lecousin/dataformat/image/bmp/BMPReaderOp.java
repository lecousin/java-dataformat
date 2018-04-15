package net.lecousin.dataformat.image.bmp;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	public AsyncWork<Pair<BufferedImage,Object>,Exception> execute(Data data, Object params, byte priority, WorkProgress progress, long work) {
		AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.openReadOnly(priority);
		Task<Pair<BufferedImage,Object>,Exception> task = new Task.Cpu<Pair<BufferedImage,Object>,Exception>("Reading BMP data", priority) {
			@SuppressWarnings("resource")
			@Override
			public Pair<BufferedImage,Object> run() throws Exception {
				if (!open.isSuccessful())
					throw open.getError();
				if (progress != null) progress.progress(work/5);
				try (IO.Readable.Seekable io = open.getResult()) {
					return new Pair<>(ImageIO.read(IOAsInputStream.get(new ReadableWithProgress(io, data.getSize(), progress, work-work/5))), null);
				} catch (Throwable t) {
					throw new Exception("Error reading BMP data", t);
				} finally {
				}
			}
		};
		open.listenAsync(task, true);
		return task.getOutput();
	}
	
	@Override
	public void release(Data data, Pair<BufferedImage,Object> output) {
	}
	
}
