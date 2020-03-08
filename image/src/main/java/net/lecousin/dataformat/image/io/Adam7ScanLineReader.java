package net.lecousin.dataformat.image.io;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;

import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public abstract class Adam7ScanLineReader extends ImageReader {

	public static Adam7ScanLineReader createIndexed(int width, int height, int bits, AsyncSupplier<IndexColorModel,Exception> colorModel, boolean littleEndian, ScanLineHandler scanner) throws InvalidImage {
		if (bits < 1 || bits > 8)
			throw new InvalidImage("Invalid bit depth for indexed color image: "+bits);
		BitsReader reader = new BitsReader(colorModel, bits, width, height, littleEndian, scanner);
		if (bits < 8)
			reader.raster = Raster.createPackedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, bits, null);
		else
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
        return reader;
	}
	
	public static Adam7ScanLineReader createGreyscale(int width, int height, int bits, int transparentPixel, PixelSampleModifier sampleModifier, boolean littleEndian, ScanLineHandler scanner, Priority priority) throws InvalidImage {
		if (bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16)
			throw new InvalidImage("Invalid bit depth for greyscale image: "+bits);
		if (bits < 8 || (bits == 8 && transparentPixel >= 0)) {
			Task<IndexColorModel, Exception> createCM = Task.cpu("Create greyscale index color model", priority, t -> {
				// indexed color model
	            int numEntries = 1 << bits;
	            byte[] arr = new byte[numEntries];
	            for (int i = 0; i < numEntries; i++)
	            	if (sampleModifier != null)
	            		arr[i] = (byte)sampleModifier.modifyPixelSample(i*255/(numEntries - 1), 8);
	            	else
	            		arr[i] = (byte)(i*255/(numEntries - 1));
	            if (transparentPixel < 0)
	            	return new IndexColorModel(bits, numEntries, arr, arr, arr);
	            return new IndexColorModel(bits, numEntries, arr, arr, arr, transparentPixel);
			});
			createCM.start();
			BitsReader reader = new BitsReader(createCM.getOutput(), bits, width, height, littleEndian, scanner);
            if (bits < 8)
            	reader.raster = Raster.createPackedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, bits, null);
            else
            	reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
            return reader;
		}
		if (bits == 8) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 1, 0, sampleModifier, scanner);
            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
            return reader;
		}
		// 16 bits
		if (transparentPixel >= 0) {
			// for transparent pixel, we use indexed color model
			// so we will convert the image into 8 bits
			Task<IndexColorModel, Exception> createCM = Task.cpu("Create greyscale index color model", priority, t -> {
	            byte[] arr = new byte[256];
	            for (int i = 0; i < 256; i++)
	            	if (sampleModifier != null)
	            		arr[i] = (byte)sampleModifier.modifyPixelSample(i, 8);
	            	else
	            		arr[i] = (byte)i;
	            return new IndexColorModel(bits, 255, arr, arr, arr, transparentPixel>>8);
			});
			createCM.start();
        	UShortToByteReaderWithTransparentPixel reader = new UShortToByteReaderWithTransparentPixel(createCM.getOutput(), width, height, transparentPixel, littleEndian, scanner);
            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
            return reader;
		}
		// 16 bits without transparent pixel => component model
		Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] {16}, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
		);
		createCM.start();
		UShortReader reader = new UShortReader(createCM.getOutput(), width, height, 1, false, littleEndian, sampleModifier, scanner);
        reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] {0}, null);
        return reader;
	}

	public static Adam7ScanLineReader createGreyscaleWithAlpha(int width, int height, int bits, PixelSampleModifier sampleModifier, boolean littleEndian, ScanLineHandler scanner, Priority priority) throws InvalidImage {
		if (bits != 8 && bits != 16)
			throw new InvalidImage("Invalid bit depth for greyscale image with alpha: "+bits);
		if (bits == 8) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8, 8 }, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 1, 1, sampleModifier, scanner);
            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width*2, 2, new int[] { 0, 1 }, null);
            return reader;
		}
		// 16 bits
		Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] {16,16}, true, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
		);
		createCM.start();
		UShortReader reader = new UShortReader(createCM.getOutput(), width, height, 2, true, littleEndian, sampleModifier, scanner);
        reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, width*2, 2, new int[] {0,1}, null);
        return reader;
	}
	
	
	public static Adam7ScanLineReader createTrueColor(int width, int height, int bitsPerComponent, PixelSampleModifier sampleModifier, boolean littleEndian, ScanLineHandler scanner, Priority priority) throws InvalidImage {
		return createTrueColor(width, height, bitsPerComponent, -1, -1, -1, sampleModifier, littleEndian, scanner, priority);
	}
	public static Adam7ScanLineReader createTrueColor(int width, int height, int bitsPerComponent, int transparentR, int transparentG, int transparentB, PixelSampleModifier sampleModifier, boolean littleEndian, ScanLineHandler scanner, Priority priority) throws InvalidImage {
		if (bitsPerComponent == 8) {
			if (transparentR < 0) {
				// no transparency => normal direct color model
				Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
					new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
				);
				createCM.start();
				ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 3, 0, sampleModifier, scanner);
				reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, 3*width, 3, new int[] { 0, 1, 2 }, null);
	            return reader;
			}
			// with a transparent value, we need to add a bit for alpha
			Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 25, 0xff0000, 0x00ff00, 0x0000ff, 0x1000000, false, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			// we cannot read bytes directly (because we need to add alpha bit), so we will use integers
			RGBWithTransparentValueReader reader = new RGBWithTransparentValueReader(createCM.getOutput(), width, height, transparentR, transparentG, transparentB, sampleModifier, scanner);
			reader.raster = Raster.createPackedRaster(new DataBufferInt(reader.buffer, reader.buffer.length), width, height, width, new int[] {0xff0000, 0x00ff00, 0x0000ff, 0x1000000}, null);
            return reader;
		} else if (bitsPerComponent == 16) {
			if (transparentR < 0) {
				// we cannot use direct color model because each pixel will be 48 bits and cannot be stored in an integer
				Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
					new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
				);
				createCM.start();
				UShortReader reader = new UShortReader(createCM.getOutput(), width, height, 3, false, littleEndian, sampleModifier, scanner);
				reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, 3*width, 3, new int[] { 0, 1, 2 }, null);
	            return reader;
			}
			// with a transparent value we will need an alpha bit
			// to make it simpler, we will convert the 3 ushort samples + the alpha bit into integers
			// and we will use a direct color model
			Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 25, 0xff0000, 0x00ff00, 0x0000ff, 0x1000000, false, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			UShortRGBWithTransparentPixelReader reader = new UShortRGBWithTransparentPixelReader(createCM.getOutput(), width, height, transparentR, transparentG, transparentB, littleEndian, sampleModifier, scanner);
			reader.raster = Raster.createPackedRaster(new DataBufferInt(reader.buffer, reader.buffer.length), width, height, width, new int[] {0xff0000, 0x00ff00, 0x0000ff, 0x1000000}, null);
            return reader;
		} else
			throw new InvalidImage("Invalid number of bits per component for true color image: "+bitsPerComponent+", allowed values are 8 and 16");
	}

	public static Adam7ScanLineReader createRGBA(int width, int height, int bitsPerComponent, PixelSampleModifier sampleModifier, boolean littleEndian, ScanLineHandler scanner, Priority priority) throws InvalidImage {
		if (bitsPerComponent == 8) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 3, 1, sampleModifier, scanner);
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, 4*width, 4, new int[] { 0, 1, 2, 3 }, null);
            return reader;
		} else if (bitsPerComponent == 16) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT)
			);
			createCM.start();
			UShortReader reader = new UShortReader(createCM.getOutput(), width, height, 4, true, littleEndian, sampleModifier, scanner);
			reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, 4*width, 4, new int[] { 0, 1, 2, 3 }, null);
            return reader;
		} else
			throw new InvalidImage("Invalid number of bits per component for true color image: "+bitsPerComponent+", allowed values are 8 and 16");
	}

	
	protected Adam7ScanLineReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, ScanLineHandler scanner) {
		super(colorModel);
		this.width = width;
		this.height = height;
		this.scanner = scanner;
	}
	protected int width, height;
	protected ScanLineHandler scanner;

	@Override
	protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
		Async<Exception> done = new Async<>();
		Task.cpu("Adam7 Scan Line Reader", io.getPriority(), t -> {
			scan(io, done);
			return null;
		}).start();
		// TODO listen to cancel
		return done;
	}
	protected abstract int getBitsPerPixel();
	protected abstract void readPixel(int x, int y, byte[] line, int indexInLine);
	protected abstract void readLine(int y, byte[] line);
	
	private void scan(IO.ReadableByteStream io, Async<Exception> done) {
		int pixelsPerLine, lines;
		for (int pass = 1; pass <= 7; ++pass) {
			switch (pass) {
			default:
			case 1:
				pixelsPerLine = width/8 + ((width % 8) != 0 ? 1 : 0);
				lines = height/8 + ((height%8) != 0 ? 1 : 0);
				break;
			case 2:
				pixelsPerLine = width/8 + ((width % 8) >= 5 ? 1 : 0);
				lines = height/8 + ((height%8) != 0 ? 1 : 0);
				break;
			case 3:
				pixelsPerLine = width/4 + ((width % 4) != 0 ? 1 : 0);
				lines = height/8 + ((height%8) >= 5 ? 1 : 0);
				break;
			case 4:
				pixelsPerLine = width/4 + ((width % 4) == 3 ? 1 : 0);
				lines = height/4 + ((height%4) != 0 ? 1 : 0);
				break;
			case 5:
				pixelsPerLine = width/2 + (width%2);
				lines = height/4 + ((height%4) == 3 ? 1 : 0);
				break;
			case 6:
				pixelsPerLine = width/2;
				lines = height/2 + (height%2);
				break;
			case 7:
				pixelsPerLine = width;
				lines = height/2;
				break;
			}
			//System.out.println("Pass "+pass+": "+pixelsPerLine+" pixels per line, "+getBitsPerPixel()+" bits per pixel = "+scanner.getBytesToReadPerLine(pixelsPerLine, getBitsPerPixel())+" bytes per line, for "+lines+" lines");
			byte[] lineBuffer = new byte[scanner.getBytesToReadPerLine(pixelsPerLine, getBitsPerPixel())];
			int bytesPerLine = pixelsPerLine * getBitsPerPixel();
			bytesPerLine = (bytesPerLine/8) + ((bytesPerLine%8) != 0 ? 1 : 0);
			byte[] lineOutput = new byte[bytesPerLine];
			int line = 0;
			do {
				if (line >= lines || lineBuffer.length == 0) {
					if (pass < 7) {
						scanner.reset();
						break;
					}
					done.unblock();
					return;
				}
				int read;
				try { read = io.readFully(lineBuffer); }
				catch (IOException e) {
					done.error(e);
					return;
				}
				if (read != lineBuffer.length) {
					done.error(new InvalidImage("Unexpected end of image data: pass "+pass+", line "+(line+1)+", "+read+" bytes found, "+lineBuffer.length+" expected"));
					return;
				}
				try { scanner.scan(lineBuffer, lineOutput, 0); }
				catch (InvalidImage e) {
					done.error(e);
					return;
				}
				switch (pass) {
				case 1:
					for (int x = 0; x < width; x += 8)
						readPixel(x, line*8, lineOutput, x/8);
					break;
				case 2:
					for (int x = 4; x < width; x += 8)
						readPixel(x, line*8, lineOutput, (x-4)/8);
					break;
				case 3:
					for (int x = 0; x < width; x += 4)
						readPixel(x, 4+line*8, lineOutput, x/4);
					break;
				case 4:
					for (int x = 2; x < width; x += 4)
						readPixel(x, line*4, lineOutput, (x-2)/4);
					break;
				case 5:
					for (int x = 0; x < width; x += 2)
						readPixel(x, 2+line*4, lineOutput, x/2);
					break;
				case 6:
					for (int x = 1; x < width; x += 2)
						readPixel(x, line*2, lineOutput, x/2);
					break;
				case 7:
					readLine(1+line*2, lineOutput);
					break;
				}
				line++;
			} while(true);
		}
	}

	/* *** Readers *** */
	
	private static class ByteReader extends Adam7ScanLineReader {
		public ByteReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int bytes, int alphaBytes, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			buffer = new byte[width*height*(bytes+alphaBytes)];
			this.sampleModifier = sampleModifier;
			this.bytes = bytes;
			this.alphaBytes = alphaBytes;
		}
		private byte[] buffer;
		private int bytes;
		private int alphaBytes;
		private PixelSampleModifier sampleModifier;
		@Override
		protected int getBitsPerPixel() {
			return (bytes+alphaBytes)*8;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			int p = bytes+alphaBytes;
			if (sampleModifier == null) {
				System.arraycopy(line, indexInLine*p, buffer, (y*width+x)*p, p);
			} else {
				for (int i = 0; i < bytes; ++i)
					buffer[(y*width+x)*p+i] = (byte)sampleModifier.modifyPixelSample(line[indexInLine*p+i], 8);
				for (int i = 0; i < alphaBytes; ++i)
					buffer[(y*width+x)*p+bytes+i] = line[indexInLine*p+bytes+i];
			}
		}
		@Override
		protected void readLine(int y, byte[] line) {
			int p = bytes+alphaBytes;
			if (sampleModifier == null)
				System.arraycopy(line, 0, buffer, (y*width)*p, line.length);
			else if (alphaBytes == 0) {
				for (int x = 0; x < width*bytes; ++x)
						buffer[y*width*p+x] = (byte)sampleModifier.modifyPixelSample(line[x], 8);
			} else {
				for (int x = 0; x < width; ++x) {
					for (int i = 0; i < bytes; ++i)
						buffer[(y*width+x)*p+i] = (byte)sampleModifier.modifyPixelSample(line[x*p+i], 8);
					for (int i = 0; i < alphaBytes; ++i)
						buffer[(y*width+x)*p+bytes+i] = line[x*p+bytes+i];
				}
			}
		}
	}

	private static class BitsReader extends Adam7ScanLineReader {
		public BitsReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int bits, int width, int height, boolean littleEndian, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			this.bits = bits;
			bytesPerLine = width*bits/8 + (((width*bits)%8) != 0 ? 1 : 0);
			buffer = new byte[bytesPerLine*height];
			// TODO this.littleEndian = littleEndian;
			mask = ((1 << bits)-1);
		}
		private int bits;
		private int bytesPerLine;
		private byte[] buffer;
		// TODO private boolean littleEndian;
		private int mask;
		@Override
		protected int getBitsPerPixel() {
			return bits;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			if (bits == 8)
				buffer[y*width+x] = line[indexInLine];
			else //if (littleEndian)
				// TODO check this one
				buffer[y*bytesPerLine+x*bits/8] |= (((line[indexInLine*bits/8]&0xFF) >> (8-bits-(indexInLine%(8/bits))*bits)) & mask) << (8-bits-(x%(8/bits))*bits);
			//else
				//buffer[y*bytesPerLine+x*bits/8] |= (((line[indexInLine*bits/8]&0xFF) >> (8-bits-(indexInLine%(8/bits))*bits)) & ((1 << bits)-1)) << (x%(8/bits))*bits;
		}
		@Override
		protected void readLine(int y, byte[] line) {
			System.arraycopy(line, 0, buffer, y*bytesPerLine, line.length);
		}
	}

	private static class UShortReader extends Adam7ScanLineReader {
		public UShortReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int components, boolean hasAlpha, boolean littleEndian, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			this.littleEndian = littleEndian;
			buffer = new short[width*height*components];
			this.sampleModifier = sampleModifier;
			this.components = components;
			this.hasAlpha = hasAlpha;
		}
		private short[] buffer;
		private boolean littleEndian;
		private int components;
		private boolean hasAlpha;
		private PixelSampleModifier sampleModifier;
		@Override
		protected int getBitsPerPixel() {
			return components*16;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			for (int i = 0; i < components; ++i) {
				int sample = littleEndian ? DataUtil.Read16.LE.read(line, (indexInLine*components+i)*2) : DataUtil.Read16.BE.read(line, (indexInLine*components+i)*2);
				if (sampleModifier == null || (hasAlpha && (i%components) == components-1))
					buffer[(y*width+x)*components+i] = (short)sample;
				else
					buffer[(y*width+x)*components+i] = (short)sampleModifier.modifyPixelSample(sample, 16);
			}
		}
		@Override
		protected void readLine(int y, byte[] line) {
			for (int i = 0; i < width*components; ++i) {
				int sample = littleEndian ? DataUtil.Read16.LE.read(line, i*2) : DataUtil.Read16.BE.read(line, i*2);
				if (sampleModifier == null || (hasAlpha && (i%components) == components-1))
					buffer[y*width*components+i] = (short)sample;
				else
					buffer[y*width*components+i] = (short)sampleModifier.modifyPixelSample(sample, 16);
			}
		}
	}
	
	private static class UShortToByteReaderWithTransparentPixel extends Adam7ScanLineReader {
		public UShortToByteReaderWithTransparentPixel(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transparentValue, boolean littleEndian, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			buffer = new byte[width*height];
			this.transparentValue = transparentValue;
			this.transparentHigh = transparentValue>>8;
			this.littleEndian = littleEndian;
		}
		private byte[] buffer;
		private int transparentValue;
		private int transparentHigh;
		private boolean littleEndian;
		@Override
		protected int getBitsPerPixel() {
			return 16;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			int b = (littleEndian ? line[indexInLine*2+1] : line[indexInLine*2])&0xFF;
			if (b == transparentHigh) {
				int b2 = (littleEndian ? line[indexInLine*2] : line[indexInLine*2+1])&0xFF;
				if (((b<<8)|b2) != transparentValue) {
					if (b == 255) b = 254; else b++;
				}
			}
			buffer[y*width+x] = (byte)b;
		}
		@Override
		protected void readLine(int y, byte[] line) {
			for (int x = 0; x < width; ++x) {
				int b = (littleEndian ? line[x*2+1] : line[x*2])&0xFF;
				if (b == transparentHigh) {
					int b2 = (littleEndian ? line[x*2] : line[x*2+1])&0xFF;
					if (((b<<8)|b2) != transparentValue) {
						if (b == 255) b = 254; else b++;
					}
				}
				buffer[y*width+x] = (byte)b;
			}
		}
	}
	
	private static class RGBWithTransparentValueReader extends Adam7ScanLineReader {
		public RGBWithTransparentValueReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transR, int transG, int transB, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			buffer = new int[width*height];
			transparentValue = (transR&0xFF)<<16 | (transG&0xFF)<<8 | (transB&0xFF);
			this.sampleModifier = sampleModifier;
		}
		private int[] buffer;
		private int transparentValue;
		private PixelSampleModifier sampleModifier;
		@Override
		protected int getBitsPerPixel() {
			return 24;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			int index = y*width+x;
			int pixel = ((line[index*3]&0xFF)<<16) | ((line[index*3+1]&0xFF)<<8) | (line[index*3+2]&0xFF);
			if (pixel == transparentValue)
				buffer[index] = 0;
			else if (sampleModifier == null)
				buffer[index] = 0x1000000 | pixel;
			else
				buffer[index] =
					0x1000000 |
					sampleModifier.modifyPixelSample(line[index*3]&0xFF, 8)<<16 |
					sampleModifier.modifyPixelSample(line[index*3+1]&0xFF, 8)<<8 |
					sampleModifier.modifyPixelSample(line[index*3+2]&0xFF, 8);
		}
		@Override
		protected void readLine(int y, byte[] line) {
			for (int x = 0; x < width; ++x) {
				int index = y*width+x;
				int pixel = ((line[index*3]&0xFF)<<16) | ((line[index*3+1]&0xFF)<<8) | (line[index*3+2]&0xFF);
				if (pixel == transparentValue)
					buffer[index] = 0;
				else if (sampleModifier == null)
					buffer[index] = 0x1000000 | pixel;
				else
					buffer[index] =
						0x1000000 |
						sampleModifier.modifyPixelSample(line[index*3]&0xFF, 8)<<16 |
						sampleModifier.modifyPixelSample(line[index*3+1]&0xFF, 8)<<8 |
						sampleModifier.modifyPixelSample(line[index*3+2]&0xFF, 8);
			}
		}
	}
	
	private static class UShortRGBWithTransparentPixelReader extends Adam7ScanLineReader {
		public UShortRGBWithTransparentPixelReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transparentR, int transparentG, int transparentB, boolean littleEndian, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel, width, height, scanner);
			buffer = new int[width*height];
			tR = transparentR;
			tG = transparentG;
			tB = transparentB;
			this.littleEndian = littleEndian;
			this.sampleModifier = sampleModifier;
		}
		private int[] buffer;
		private int tR, tG, tB;
		private boolean littleEndian;
		private PixelSampleModifier sampleModifier;
		@Override
		protected int getBitsPerPixel() {
			return 48;
		}
		@Override
		protected void readPixel(int x, int y, byte[] line, int indexInLine) {
			int r, g, b;
			if (littleEndian) {
				r = DataUtil.Read16U.LE.read(line, indexInLine*6);
				g = DataUtil.Read16U.LE.read(line, indexInLine*6+2);
				b = DataUtil.Read16U.LE.read(line, indexInLine*6+4);
			} else {
				r = DataUtil.Read16U.BE.read(line, indexInLine*6);
				g = DataUtil.Read16U.BE.read(line, indexInLine*6+2);
				b = DataUtil.Read16U.BE.read(line, indexInLine*6+4);
			}
			if (r == tR && g == tG && b == tB)
				buffer[y*width+x] = 0;
			else if (sampleModifier == null)
				buffer[y*width+x] = 0x1000000 | ((r&0xFF00)<<8) | (g&0xFF00) | (b>>8);
			else
				buffer[y*width+x] = 
					0x1000000 |
					(sampleModifier.modifyPixelSample(r, 16)&0xFF00)<<8 |
					(sampleModifier.modifyPixelSample(g, 16)&0xFF00) |
					(sampleModifier.modifyPixelSample(b, 16)>>8);
		}
		@Override
		protected void readLine(int y, byte[] line) {
			int r, g, b;
			for (int x = 0; x < width; ++x) {
				if (littleEndian) {
					r = DataUtil.Read16U.LE.read(line, x*6);
					g = DataUtil.Read16U.LE.read(line, x*6+2);
					b = DataUtil.Read16U.LE.read(line, x*6+4);
				} else {
					r = DataUtil.Read16U.BE.read(line, x*6);
					g = DataUtil.Read16U.BE.read(line, x*6+2);
					b = DataUtil.Read16U.BE.read(line, x*6+4);
				}
				if (r == tR && g == tG && b == tB)
					buffer[y*width+x] = 0;
				else if (sampleModifier == null)
					buffer[y*width+x] = 0x1000000 | ((r&0xFF00)<<8) | (g&0xFF00) | (b>>8);
				else
					buffer[y*width+x] = 
						0x1000000 |
						(sampleModifier.modifyPixelSample(r, 16)&0xFF00)<<8 |
						(sampleModifier.modifyPixelSample(g, 16)&0xFF00) |
						(sampleModifier.modifyPixelSample(b, 16)>>8);
			}
		}
	}
}
