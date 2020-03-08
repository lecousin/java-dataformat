package net.lecousin.dataformat.image.bmp;

import java.awt.image.BufferedImage;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsOutputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class BMPWriterOp implements DataFormatWriteOperation.OneToOne<BufferedImage, BMPDataFormat, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Write BMP");
	}
	
	@Override
	public Class<BufferedImage> getInputType() { return BufferedImage.class; }
	@Override
	public BMPDataFormat getOutputFormat() { return BMPDataFormat.instance; }
	
	@Override
	public Class<Object> getParametersClass() {
		return null;
	}
	@Override
	public Object createDefaultParameters() {
		return null;
	}
	
	@Override
	public AsyncSupplier<Void,Exception> execute(BufferedImage input, Pair<Data,IO.Writable> output, Object params, Priority priority, WorkProgress progress, long work) {
		Task<Void,Exception> task = Task.cpu("Generate BMP image", priority, t -> {
			Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("bmp");
			if (!it.hasNext())
				throw new Exception("No available BMP writer");
			ImageWriter writer = it.next();
			// TODO options ?
			writer.setOutput(new MemoryCacheImageOutputStream(IOAsOutputStream.get(output.getValue2())));
			writer.write(input);
			output.getValue1().setFormat(BMPDataFormat.instance);
			if (progress != null) progress.progress(work);
			return null;
		});
		task.start();
		return task.getOutput();
	}
	
}
