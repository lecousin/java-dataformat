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
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.util.DataUtil;

public abstract class ScanLineReader extends ImageReader {

	public static ScanLineReader createIndexed(int width, int height, int bits, int bytesPerLine, boolean bottomUp, AsyncSupplier<IndexColorModel, Exception> colorModel) throws InvalidImage {
		if (bits < 1 || bits > 8)
			throw new InvalidImage("Invalid bit depth for indexed color image: "+bits);
		if (bits == 8 && !bottomUp && bytesPerLine == width) {
			// ideal situation, we can read directly into the raster
			FullReader reader = new FullReader(colorModel, bytesPerLine * height);
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
			return reader;
		}
		DirectByteReader reader = new DirectByteReader(colorModel, width, height, bytesPerLine, bits, bottomUp);
		if (bits < 8)
			reader.raster = Raster.createPackedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, bits, null);
		else
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
		return reader;
	}
	
	public static ScanLineReader createIndexed(int width, int height, int bits, AsyncSupplier<IndexColorModel, Exception> colorModel, ScanLineHandler scanLineHandler) throws InvalidImage {
		if (bits < 1 || bits > 8)
			throw new InvalidImage("Invalid bit depth for indexed color image: "+bits);
		ByteReader reader = new ByteReader(colorModel, width, height, bits, 0, null, scanLineHandler);
		if (bits < 8)
			reader.raster = Raster.createPackedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, bits, null);
		else
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
        return reader;
	}
	
	public static ScanLineReader createGreyscale(int width, int height, int bits, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		return createGreyscale(width, height, bits, -1, sampleModifier, scanLineHandler, littleEndian, priority);
	}
	public static ScanLineReader createGreyscale(int width, int height, int bits, int transparentPixel, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		if (bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16)
			throw new InvalidImage("Invalid bit depth for greyscale image: "+bits);
		if (bits < 8 || (bits == 8 && transparentPixel >= 0)) {
			Task<IndexColorModel, Exception> createCM = Task.cpu("Create greyscale index color model", priority, t -> {
				// indexed color model
	            int numEntries = 1 << bits;
	            byte[] arr = new byte[numEntries];
	            for (int i = 0; i < numEntries; i++) {
	            	if (sampleModifier != null)
	            		arr[i] = (byte)sampleModifier.modifyPixelSample(i*255/(numEntries - 1), 8);
	            	else
	            		arr[i] = (byte)(i*255/(numEntries - 1));
	            }
	            if (transparentPixel < 0)
	            	return new IndexColorModel(bits, numEntries, arr, arr, arr);
	            return new IndexColorModel(bits, numEntries, arr, arr, arr, transparentPixel);
			});
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, bits, 0, null, scanLineHandler);
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
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 8, 0, sampleModifier, scanLineHandler);
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
	            return new IndexColorModel(8, 255, arr, arr, arr, transparentPixel>>8);
			});
        	createCM.start();
            if (littleEndian) {
            	UShortToByteReaderWithTransparentPixel reader = new UShortToByteReaderWithTransparentPixel(createCM.getOutput(), width, height, transparentPixel, true, scanLineHandler);
	            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
	            return reader;
            }
        	UShortToByteReaderWithTransparentPixel reader = new UShortToByteReaderWithTransparentPixel(createCM.getOutput(), width, height, transparentPixel, false, scanLineHandler);
            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] { 0 }, null);
            return reader;
		}
		// 16 bits without transparent pixel => component model
		Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] {16}, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
		);
		createCM.start();
		UShortReader reader = new UShortReader(createCM.getOutput(), width, height, littleEndian, 1, false, sampleModifier, scanLineHandler);
        reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, width, 1, new int[] {0}, null);
        return reader;
	}

	public static ScanLineReader createGreyscaleWithAlpha(int width, int height, int bits, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		if (bits != 8 && bits != 16)
			throw new InvalidImage("Invalid bit depth for greyscale image with alpha: "+bits);
		if (bits == 8) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8, 8 }, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 8, 8, sampleModifier, scanLineHandler);
            reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, width*2, 2, new int[] { 0, 1 }, null);
            return reader;
		}
		// 16 bits
		Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] {16,16}, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT)
		);
		createCM.start();
		UShortReader reader = new UShortReader(createCM.getOutput(), width, height, littleEndian, 2, true, sampleModifier, scanLineHandler);
        reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, width*2, 2, new int[] {0,1}, null);
        return reader;
	}
	
	public static ScanLineReader createTrueColor16Bits(int width, int height, int bytesPerLine, int redMask, int greenMask, int blueMask, int alphaMask, boolean littleEndian, boolean bottomUp, Priority priority) {
		Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new DirectColorModel(16, redMask, greenMask, blueMask, alphaMask)
		);
		createCM.start();
		DirectUShortReader reader = new DirectUShortReader(createCM.getOutput(), width, height, bytesPerLine, littleEndian, bottomUp);
		int[] bands;
		if (alphaMask == 0) bands = new int[] { redMask, greenMask, blueMask };
		else bands = new int[] { redMask, greenMask, blueMask, alphaMask };
		reader.raster = Raster.createPackedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, width, bands, null);
		return reader;
	}

	public static ScanLineReader createTrueColor32Bits(int width, int height, int bytesPerLine, int redMask, int greenMask, int blueMask, int alphaMask, boolean littleEndian, boolean bottomUp, Priority priority) {
		Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new DirectColorModel(32, redMask, greenMask, blueMask, alphaMask)
		);
		createCM.start();
		DirectIntegerReader reader = new DirectIntegerReader(createCM.getOutput(), width, height, bytesPerLine, littleEndian, bottomUp);
		int[] bands;
		if (alphaMask == 0) bands = new int[] { redMask, greenMask, blueMask };
		else bands = new int[] { redMask, greenMask, blueMask, alphaMask };
		reader.raster = Raster.createPackedRaster(new DataBufferInt(reader.buffer, reader.buffer.length), width, height, width, bands, null);
		return reader;
	}
	
	/** rgb order is new int[] { 0, 1, 2 } for RGB */
	public static ScanLineReader createTrueColor24Bits(int width, int height, int bytesPerLine, boolean bottomUp, int[] rgbOrder, Priority priority) {
		Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
			new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
		);
		createCM.start();
		DirectByteReader reader = new DirectByteReader(createCM.getOutput(), width, height, bytesPerLine, 24, bottomUp);
		reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, 3*width, 3, rgbOrder, null);
		return reader;
	}
	
	public static ScanLineReader createTrueColor(int width, int height, int bitsPerComponent, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		return createTrueColor(width, height, bitsPerComponent, -1, -1, -1, sampleModifier, scanLineHandler, littleEndian, priority);
	}
	public static ScanLineReader createTrueColor(int width, int height, int bitsPerComponent, int transparentR, int transparentG, int transparentB, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		if (bitsPerComponent == 8) {
			if (transparentR < 0) {
				// no transparency => normal direct color model
				Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
					new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
				);
				createCM.start();
				ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 24, 0, sampleModifier, scanLineHandler);
				reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, 3*width, 3, new int[] { 0, 1, 2 }, null);
	            return reader;
			}
			// with a transparent value, we need to add a bit for alpha
			Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 25, 0xff0000, 0x00ff00, 0x0000ff, 0x1000000, false, DataBuffer.TYPE_INT)
			);
			createCM.start();
			// we cannot read bytes directly (because we need to add alpha bit), so we will use integers
			RGBWithTransparentValueReader reader = new RGBWithTransparentValueReader(createCM.getOutput(), width, height, transparentR, transparentG, transparentB, sampleModifier, scanLineHandler);
			reader.raster = Raster.createPackedRaster(new DataBufferInt(reader.buffer, reader.buffer.length), width, height, width, new int[] {0xff0000, 0x00ff00, 0x0000ff, 0x1000000}, null);
            return reader;
		} else if (bitsPerComponent == 16) {
			if (transparentR < 0) {
				// we cannot use direct color model because each pixel will be 48 bits and cannot be stored in an integer
				Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
					new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
				);
				createCM.start();
				UShortReader reader = new UShortReader(createCM.getOutput(), width, height, littleEndian, 3, false, sampleModifier, scanLineHandler);
				reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, 3*width, 3, new int[] { 0, 1, 2 }, null);
	            return reader;
			}
			// with a transparent value we will need an alpha bit
			// to make it simpler, we will convert the 3 ushort samples + the alpha bit into integers
			// and we will use a direct color model
			Task<DirectColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 25, 0xff0000, 0x00ff00, 0x0000ff, 0x1000000, false, DataBuffer.TYPE_INT)
			);
			createCM.start();
			UShortRGBWithTransparentPixelReader reader = new UShortRGBWithTransparentPixelReader(createCM.getOutput(), width, height, transparentR, transparentG, transparentB, littleEndian, sampleModifier, scanLineHandler);
			reader.raster = Raster.createPackedRaster(new DataBufferInt(reader.buffer, reader.buffer.length), width, height, width, new int[] {0xff0000, 0x00ff00, 0x0000ff, 0x1000000}, null);
            return reader;
		} else
			throw new InvalidImage("Invalid number of bits per component for true color image: "+bitsPerComponent+", allowed values are 8 and 16");
	}

	public static ScanLineReader createRGBA(int width, int height, int bitsPerComponent, PixelSampleModifier sampleModifier, ScanLineHandler scanLineHandler, boolean littleEndian, Priority priority) throws InvalidImage {
		if (bitsPerComponent == 8) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)
			);
			createCM.start();
			ByteReader reader = new ByteReader(createCM.getOutput(), width, height, 24, 8, sampleModifier, scanLineHandler);
			reader.raster = Raster.createInterleavedRaster(new DataBufferByte(reader.buffer, reader.buffer.length), width, height, 4*width, 4, new int[] { 0, 1, 2, 3 }, null);
            return reader;
		} else if (bitsPerComponent == 16) {
			Task<ComponentColorModel, Exception> createCM = Task.cpu("Create color model", priority, t ->
				new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT)
			);
			createCM.start();
			UShortReader reader = new UShortReader(createCM.getOutput(), width, height, littleEndian, 4, true, sampleModifier, scanLineHandler);
			reader.raster = Raster.createInterleavedRaster(new DataBufferUShort(reader.buffer, reader.buffer.length), width, height, 4*width, 4, new int[] { 0, 1, 2, 3 }, null);
            return reader;
		} else
			throw new InvalidImage("Invalid number of bits per component for true color image: "+bitsPerComponent+", allowed values are 8 and 16");
	}
	
	protected ScanLineReader(AsyncSupplier<? extends ColorModel, Exception> colorModel) {
		super(colorModel);
	}
	
	private static class FullReader extends ScanLineReader {
		public FullReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int dataSize) {
			super(colorModel);
			buffer = new byte[dataSize];
		}
		private byte[] buffer;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Task<Void, Exception> task = Task.cpu("Full image reader", io.getPriority(), t -> {
				io.readFully(buffer);
				return null;
			});
			task.startOn(io.canStartReading(), true);
			return task.getOutput();
		}
	}
	
	private static class ByteReader extends ScanLineReader {
		public ByteReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int bitsPerPixel, int alphaBitsPerPixel, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel);
			this.width = width;
			this.height = height;
			this.bitsPerPixel = bitsPerPixel;
			this.alphaBitsPerPixel = alphaBitsPerPixel;
			this.sampleModifier = sampleModifier;
			int bits = width*(bitsPerPixel + alphaBitsPerPixel);
			int lineBytes = bits/8 + ((bits%8) == 0 ? 0 : 1);
			buffer = new byte[height*lineBytes];
			lineBuffer = new byte[scanner.getBytesToReadPerLine(width, bitsPerPixel+alphaBitsPerPixel)];
			this.scanner = scanner;
		}
		private int width, height;
		private byte[] buffer;
		private byte[] lineBuffer;
		private int bitsPerPixel, alphaBitsPerPixel;
		private PixelSampleModifier sampleModifier;
		private ScanLineHandler scanner;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				int bits = width*(bitsPerPixel + alphaBitsPerPixel);
				int lineBytes = bits/8 + ((bits%8) == 0 ? 0 : 1);
				int colorBytes = bitsPerPixel/8;
				int alphaBytes = alphaBitsPerPixel/8;
				int bytesPerPixel = colorBytes + alphaBytes;
				for (int line = 0; line < height; line++) {
					int nb;
					try { nb = io.readFully(lineBuffer); }
					catch (IOException e) {
						done.error(e);
						return null;
					}
					if (nb != lineBuffer.length) {
						done.error(new InvalidImage("Unexpected end of image data: "+nb+" bytes found for line "+(line+1)+", "+lineBuffer.length+" expected"));
						return null;
					}
					try { scanner.scan(lineBuffer, buffer, line*lineBytes); }
					catch (InvalidImage e) {
						done.error(e);
						return null;
					}
					if (sampleModifier != null) {
						for (int i = 0; i < lineBytes; ++i)
							if ((i%bytesPerPixel) < colorBytes)
								buffer[line*lineBytes+i] = (byte)sampleModifier.modifyPixelSample(buffer[line*lineBytes+i]&0xFF, 8);
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}

	private static class DirectByteReader extends ScanLineReader {
		public DirectByteReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int bytesPerLine, int bitsPerPixel, boolean bottomUp) {
			super(colorModel);
			this.height = height;
			this.bottomUp = bottomUp;
			this.usefulBytesPerLine = bitsPerPixel * width;
			if ((this.usefulBytesPerLine % 8) != 0)
				this.usefulBytesPerLine = this.usefulBytesPerLine / 8 + 1;
			else
				this.usefulBytesPerLine = this.usefulBytesPerLine / 8;
			padding = bytesPerLine - usefulBytesPerLine;
			buffer = new byte[height*usefulBytesPerLine];
		}
		private int height;
		private byte[] buffer;
		private int usefulBytesPerLine;
		private int padding;
		private boolean bottomUp;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				for (int line = 0; line < height; line++) {
					int off = (bottomUp ? (height - line - 1) : line) * usefulBytesPerLine;
					try {
						IOUtil.readFully(io, buffer, off, usefulBytesPerLine);
						if (padding > 0) io.skip(padding);
					} catch (IOException e) {
						done.error(e);
						return null;
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
	private static class UShortReader extends ScanLineReader {
		public UShortReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, boolean littleEndian, int components, boolean hasAlpha, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel);
			this.width = width;
			this.height = height;
			this.components = components;
			this.littleEndian = littleEndian;
			this.sampleModifier = sampleModifier;
			this.hasAlpha = hasAlpha;
			buffer = new short[width*height*components];
			lineBuffer = new byte[scanner.getBytesToReadPerLine(width, components*16)];
			lineOutput = new byte[width*components*2];
			this.scanner = scanner;
		}
		private int width, height, components;
		private short[] buffer;
		private byte[] lineBuffer;
		private byte[] lineOutput;
		private boolean littleEndian;
		private boolean hasAlpha;
		private PixelSampleModifier sampleModifier;
		private ScanLineHandler scanner;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				for (int line = 0; line < height; ++line) {
					int nb;
					try { nb = io.readFully(lineBuffer); }
					catch (IOException e) {
						done.error(e);
						return null;
					}
					if (nb != lineBuffer.length) {
						done.error(new InvalidImage("Unexpected end of image data: "+nb+" bytes found for line "+(line+1)+", "+lineBuffer.length+" expected"));
						return null;
					}
					try { scanner.scan(lineBuffer, lineOutput, 0); }
					catch (InvalidImage e) {
						done.error(e);
						return null;
					}
					for (int i = 0; i < width*components; ++i) {
						int sample = littleEndian ? DataUtil.Read16U.LE.read(lineOutput, i*2) : DataUtil.Read16U.BE.read(lineOutput, i*2);
						if (sampleModifier == null || (hasAlpha && (i%components) == components-1))
							buffer[line*width*components+i] = (short)sample;
						else
							buffer[line*width*components+i] = (short)sampleModifier.modifyPixelSample(sample, 16);
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
	private static class DirectUShortReader extends ScanLineReader {
		public DirectUShortReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int bytesPerLine, boolean littleEndian, boolean bottomUp) {
			super(colorModel);
			this.width = width;
			this.height = height;
			this.bottomUp = bottomUp;
			this.littleEndian = littleEndian;
			padding = bytesPerLine - (2 * width);
			buffer = new short[height*width];
			lineBuffer = new byte[width * 2];
		}
		private int width;
		private int height;
		private short[] buffer;
		private byte[] lineBuffer;
		private int padding;
		private boolean bottomUp;
		private boolean littleEndian;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				for (int line = 0; line < height; line++) {
					int off = (bottomUp ? (height - line - 1) : line) * width;
					try {
						IOUtil.readFully(io, lineBuffer);
						if (padding > 0) io.skip(padding);
					} catch (IOException e) {
						done.error(e);
						return null;
					}
					if (littleEndian)
						for (int i = 0; i < width; ++i)
							buffer[off+i] = DataUtil.Read16.LE.read(lineBuffer, i*2);
					else
						for (int i = 0; i < width; ++i)
							buffer[off+i] = DataUtil.Read16.BE.read(lineBuffer, i*2);
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
	
	private static class UShortToByteReaderWithTransparentPixel extends ScanLineReader {
		public UShortToByteReaderWithTransparentPixel(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transparentValue, boolean littleEndian, ScanLineHandler scanner) {
			super(colorModel);
			this.width = width;
			this.height = height;
			this.transparentValue = transparentValue;
			this.littleEndian = littleEndian;
			this.buffer = new byte[width*height];
			this.lineBuffer = new byte[scanner.getBytesToReadPerLine(width, 16)];
			this.lineOutput = new byte[width*2];
			this.scanner = scanner;
		}
		private int width, height;
		private boolean littleEndian;
		private byte[] buffer;
		private int transparentValue;
		private byte[] lineBuffer;
		private byte[] lineOutput;
		private ScanLineHandler scanner;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				int transp = transparentValue>>8;
				for (int line = 0; line < height; ++line) {
					int nb;
					try { nb = io.readFully(lineBuffer); }
					catch (IOException e) {
						done.error(e);
						return null;
					}
					if (nb != lineBuffer.length) {
						done.error(new InvalidImage("Unexpected end of image data: "+nb+" bytes found for line "+(line+1)+", "+lineBuffer.length+" expected"));
						return null;
					}
					try { scanner.scan(lineBuffer, lineOutput, 0); }
					catch (InvalidImage e) {
						done.error(e);
						return null;
					}
					if (littleEndian) {
						for (int i = 0; i < width; ++i) {
							int v = lineOutput[i*2+1]&0xFF;
							if (v == transp && (lineOutput[i*2]&0xFF) == (transparentValue&0xFF)) {
								if (v == 255) v = 254; else v++;
							}
							buffer[line*width+i] = (byte)v;
						}
					} else {
						for (int i = 0; i < width; ++i) {
							int v = lineOutput[i*2]&0xFF;
							if (v == transp && (lineOutput[i*2+1]&0xFF) == (transparentValue&0xFF)) {
								if (v == 255) v = 254; else v++;
							}
							buffer[line*width+i] = (byte)v;
						}
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
	private static class RGBWithTransparentValueReader extends ScanLineReader {
		public RGBWithTransparentValueReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transR, int transG, int transB, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel);
			this.width = width;
			this.height = height;
			buffer = new int[width*height];
			transparentValue = (transR&0xFF)<<16 | (transG&0xFF)<<8 | (transB&0xFF);
			this.sampleModifier = sampleModifier;
			this.scanner = scanner;
			this.lineBuffer = new byte[scanner.getBytesToReadPerLine(width, 24)];
			this.lineOutput = new byte[width*3];
		}
		private int width, height;
		private int[] buffer;
		private byte[] lineBuffer, lineOutput;
		private int transparentValue;
		private PixelSampleModifier sampleModifier;
		private ScanLineHandler scanner;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				for (int line = 0; line < height; ++line) {
					int nb;
					try { nb = io.readFully(lineBuffer); }
					catch (IOException e) {
						done.error(e);
						return null;
					}
					if (nb != lineBuffer.length) {
						done.error(new InvalidImage("Unexpected end of image data: "+nb+" bytes found for line "+(line+1)+", "+lineBuffer.length+" expected"));
						return null;
					}
					try { scanner.scan(lineBuffer, lineOutput, 0); }
					catch (InvalidImage e) {
						done.error(e);
						return null;
					}
					for (int i = 0; i < width; ++i) {
						int pixel = ((lineOutput[i*3]&0xFF)<<16) | ((lineOutput[i*3+1]&0xFF)<<8) | (lineOutput[i*3+2]&0xFF);
						if (pixel == transparentValue)
							pixel = 0;
						else
							if (sampleModifier != null) pixel = 0x1000000 | sampleModifier.modifyPixelSample(pixel, 24);
						buffer[line*width+i] = pixel;
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}

	private static class UShortRGBWithTransparentPixelReader extends ScanLineReader {
		public UShortRGBWithTransparentPixelReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int transR, int transG, int transB, boolean littleEndian, PixelSampleModifier sampleModifier, ScanLineHandler scanner) {
			super(colorModel);
			this.width = width;
			this.height = height;
			buffer = new int[width*height];
			tR = transR;
			tG = transG;
			tB = transB;
			this.littleEndian = littleEndian;
			this.sampleModifier = sampleModifier;
			this.scanner = scanner;
			lineBuffer = new byte[scanner.getBytesToReadPerLine(width, 48)];
			lineOutput = new byte[width*6];
		}
		private int width, height;
		private boolean littleEndian;
		private int[] buffer;
		private byte[] lineBuffer, lineOutput;
		private int tR, tG, tB;
		private PixelSampleModifier sampleModifier;
		private ScanLineHandler scanner;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				int r,g,b;
				for (int line = 0; line < height; ++line) {
					int nb;
					try { nb = io.readFully(lineBuffer); }
					catch (IOException e) {
						done.error(e);
						return null;
					}
					if (nb != lineBuffer.length) {
						done.error(new InvalidImage("Unexpected end of image data: "+nb+" bytes found for line "+(line+1)+", "+lineBuffer.length+" expected"));
						return null;
					}
					try { scanner.scan(lineBuffer, lineOutput, 0); }
					catch (InvalidImage e) {
						done.error(e);
						return null;
					}
					for (int i = 0; i < width; ++i) {
						if (littleEndian) {
							r = DataUtil.Read16U.LE.read(lineOutput, i*6);
							g = DataUtil.Read16U.LE.read(lineOutput, i*6+2);
							b = DataUtil.Read16U.LE.read(lineOutput, i*6+4);
						} else {
							r = DataUtil.Read16U.BE.read(lineOutput, i*6);
							g = DataUtil.Read16U.BE.read(lineOutput, i*6+2);
							b = DataUtil.Read16U.BE.read(lineOutput, i*6+4);
						}
						if (r == tR && g == tG && b == tB)
							buffer[line*width+i] = 0;
						else if (sampleModifier == null)
							buffer[line*width+i] = 0x1000000 | ((r&0xFF00)<<8) | (g&0xFF00) | (b>>8);
						else
							buffer[line*width+i] = 0x1000000 | (sampleModifier.modifyPixelSample((r&0xFF00)>>8,8)<<16) | (sampleModifier.modifyPixelSample((g&0xFF00)>>8,8)<<8 | sampleModifier.modifyPixelSample((b&0xFF00)>>8,8));
					}
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
	private static class DirectIntegerReader extends ScanLineReader {
		public DirectIntegerReader(AsyncSupplier<? extends ColorModel, Exception> colorModel, int width, int height, int bytesPerLine, boolean littleEndian, boolean bottomUp) {
			super(colorModel);
			this.width = width;
			this.height = height;
			this.bottomUp = bottomUp;
			this.littleEndian = littleEndian;
			padding = bytesPerLine - (4 * width);
			buffer = new int[height*width];
			lineBuffer = new byte[width * 4];
		}
		private int width;
		private int height;
		private int[] buffer;
		private byte[] lineBuffer;
		private int padding;
		private boolean bottomUp;
		private boolean littleEndian;
		@Override
		protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
			Async<Exception> done = new Async<>();
			Task.cpu("Scan Line Reader", io.getPriority(), t -> {
				for (int line = 0; line < height; line++) {
					int off = (bottomUp ? (height - line - 1) : line) * width;
					try {
						IOUtil.readFully(io, lineBuffer);
						if (padding > 0) io.skip(padding);
					} catch (IOException e) {
						done.error(e);
						return null;
					}
					if (littleEndian)
						for (int i = 0; i < width; ++i)
							buffer[off+i] = DataUtil.Read32.LE.read(lineBuffer, i*4);
					else
						for (int i = 0; i < width; ++i)
							buffer[off+i] = DataUtil.Read32.BE.read(lineBuffer, i*4);
				}
				done.unblock();
				return null;
			}).start();
			return done;
		}
	}
	
}
