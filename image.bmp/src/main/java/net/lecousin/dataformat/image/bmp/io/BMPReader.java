package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public class BMPReader {

	public static AsyncSupplier<BufferedImage, Exception> read(IO.Readable input) {
		// read BMP header
		byte[] bmpHeader = new byte[14];
		AsyncSupplier<Integer, IOException> readBMPHeader = input.readFullyAsync(ByteBuffer.wrap(bmpHeader));
		DIBHeader header = new DIBHeader();
		AsyncSupplier<BufferedImage, Exception> result = new AsyncSupplier<>();
		readBMPHeader.onDone(new Runnable() {
			@Override
			public void run() {
				if (readBMPHeader.hasError()) { result.error(readBMPHeader.getError()); return; }
				if (readBMPHeader.isCancelled()) { result.cancel(readBMPHeader.getCancelEvent()); return; }
				if (readBMPHeader.getResult().intValue() != 14) { result.error(new Exception("Invalid BMP: only " + readBMPHeader.getResult().intValue() + " byte(s)")); return; }
				
				long pixelsOffset = DataUtil.Read32U.LE.read(bmpHeader, 10);
				
				AsyncSupplier<Integer, Exception> readDIBHeader = DIBReader.readHeader(input, header, pixelsOffset - 14);
				Task<Void, NoException> read = Task.cpu("Read BMP", input.getPriority(), t -> {
					DIBReader.readBitmap(header, input).forward(result);
					return null;
				});
				readDIBHeader.onDone(new Runnable() {
					@Override
					public void run() {
						if (readDIBHeader.hasError()) { result.error(readDIBHeader.getError()); return; }
						if (readDIBHeader.isCancelled()) { result.cancel(readDIBHeader.getCancelEvent()); return; }
						int off = readDIBHeader.getResult().intValue();
						if (off < pixelsOffset - 14) {
							input.skipAsync(pixelsOffset - 14 - off).thenStart(read, true);
						} else {
							read.start();
						}
					}
				});
			}
		});
		return result;
	}
	
}
