package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public class BMPReader {

	public static AsyncWork<BufferedImage, Exception> read(IO.Readable input) {
		// read BMP header
		byte[] bmpHeader = new byte[14];
		AsyncWork<Integer, IOException> readBMPHeader = input.readFullyAsync(ByteBuffer.wrap(bmpHeader));
		DIBHeader header = new DIBHeader();
		AsyncWork<BufferedImage, Exception> result = new AsyncWork<>();
		readBMPHeader.listenInline(new Runnable() {
			@Override
			public void run() {
				if (readBMPHeader.hasError()) { result.error(readBMPHeader.getError()); return; }
				if (readBMPHeader.isCancelled()) { result.cancel(readBMPHeader.getCancelEvent()); return; }
				if (readBMPHeader.getResult().intValue() != 14) { result.error(new Exception("Invalid BMP: only " + readBMPHeader.getResult().intValue() + " byte(s)")); return; }
				
				long pixelsOffset = DataUtil.readUnsignedIntegerLittleEndian(bmpHeader, 10);
				
				AsyncWork<Integer, Exception> readDIBHeader = DIBReader.readHeader(input, header, pixelsOffset - 14);
				Task.Cpu<Void, NoException> read = new Task.Cpu<Void, NoException>("Read BMP", input.getPriority()) {
					@Override
					public Void run() {
						DIBReader.readBitmap(header, input).listenInline(result);
						return null;
					}
				};
				readDIBHeader.listenInline(new Runnable() {
					@Override
					public void run() {
						if (readDIBHeader.hasError()) { result.error(readDIBHeader.getError()); return; }
						if (readDIBHeader.isCancelled()) { result.cancel(readDIBHeader.getCancelEvent()); return; }
						int off = readDIBHeader.getResult().intValue();
						if (off < pixelsOffset - 14) {
							input.skipAsync(pixelsOffset - 14 - off).listenAsync(read, true);
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
