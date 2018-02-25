package net.lecousin.dataformat.image.jpeg;

import java.awt.image.BufferedImage;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsOutputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class JPEGWriterOp implements DataFormatWriteOperation.OneToOne<BufferedImage, JPEGDataFormat, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Write JPEG");
	}
	
	@Override
	public Class<BufferedImage> getInputType() { return BufferedImage.class; }
	@Override
	public JPEGDataFormat getOutputFormat() { return JPEGDataFormat.instance; }
	
	@Override
	public Class<Object> getParametersClass() {
		return null;
	}
	@Override
	public Object createDefaultParameters() {
		return null;
	}
	
	@Override
	public AsyncWork<Void,Exception> execute(BufferedImage input, Pair<Data,IO.Writable> output, Object params, byte priority, WorkProgress progress, long work) {
		Task<Void,Exception> task = new Task.Cpu<Void,Exception>("Generate JPEG image", priority) {
			@SuppressWarnings("resource")
			@Override
			public Void run() throws Exception {
				Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
				if (!it.hasNext())
					throw new Exception("No available JPEG writer");
				ImageWriter writer = it.next();
				// TODO options ?
				writer.setOutput(new MemoryCacheImageOutputStream(IOAsOutputStream.get(output.getValue2())));
				writer.write(input);
				output.getValue1().setFormat(JPEGDataFormat.instance);
				if (progress != null) progress.progress(work);
				return null;
			}
		};
		task.start();
		return task.getOutput();
	}
	
}
