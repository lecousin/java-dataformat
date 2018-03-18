package net.lecousin.dataformat.image.jpeg;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class JPEGReaderOp implements DataFormatReadOperation.OneToOne<JPEGDataFormat, BufferedImage, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read JPEG");
	}
	
	@Override
	public JPEGDataFormat getInputFormat() { return JPEGDataFormat.instance; }
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
		Task<Pair<BufferedImage,Object>,Exception> task = new Task.Cpu<Pair<BufferedImage,Object>,Exception>("Reading JPEG data", priority) {
			@Override
			public Pair<BufferedImage,Object> run() throws Exception {
				if (!open.isSuccessful())
					throw open.getError();
				if (progress != null) progress.progress(work/4);
				try (IO.Readable.Seekable io = open.getResult()) {
					return new Pair<>(ImageIO.read(IOAsInputStream.get(io)), null);
				} catch (Throwable t) {
					throw new Exception("Error reading JPEG data", t);
				} finally {
					if (progress != null) progress.progress(work-work/4);
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
