package net.lecousin.dataformat.image.ico;

import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.dataformat.image.bmp.DIBReaderOp;
import net.lecousin.dataformat.image.bmp.DIBReaderOp.DIBImageProvider;
import net.lecousin.dataformat.image.bmp.io.DIBHeader;
import net.lecousin.dataformat.image.bmp.io.DIBReader;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public abstract class ICOCURFormat extends ImageDataFormat.Multiple {

	public static Log log = LogFactory.getLog("ICO/CUR format");
	
	public static final String IMAGE_PROPERTY = "net.lecousin.dataformat.image.ico.Image";
	
	public static abstract class Image {
		
		public int width;
		public int height;
		public int paletteColors;
		public long dataSize;
		public long dataOffset;
		
	}
	
	public static class Icon extends Image {
		
		public int colorPlanes;
		public int bitsPerPixel;
		
	}
	
	public static class Cursor extends Image {
		
		public int hotspotX;
		public int hotspotY;
		
	}
	
	public static class ImageDirectory {
		public List<Image> images;
	}
	
	public static ImageDirectory readImageDirectory(IO.Readable.Buffered io, ICOCURFormat format) throws IOException {
		if (format == null) {
			io.skip(2);
			byte b = io.readByte();
			if (b == 1) format = ICODataFormat.instance;
			else format = CURDataFormat.instance;
			io.skip(1);
		} else
			io.skip(4);
		int nbImages = DataUtil.readUnsignedShortLittleEndian(io);
		ImageDirectory dir = new ImageDirectory();
		dir.images = new ArrayList<>(nbImages);
		for (int i = 0; i < nbImages; ++i) {
			Image img = format == ICODataFormat.instance ? new Icon() : new Cursor();
			img.width = io.read();
			if (img.width < 0) throw new EOFException("Unexpected end");
			if (img.width == 0) img.width = 256;
			img.height = io.read();
			if (img.height < 0) throw new EOFException("Unexpected end");
			if (img.height == 0) img.height = 256;
			img.paletteColors = io.read();
			if (img.paletteColors < 0) throw new EOFException("Unexpected end");
			io.skip(1);
			if (format == ICODataFormat.instance) {
				((Icon)img).colorPlanes = DataUtil.readUnsignedShortLittleEndian(io);
				if (((Icon)img).colorPlanes > 1) throw new EOFException("Invalid number of color planes");
				((Icon)img).bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(io);
			} else {
				((Cursor)img).hotspotX = DataUtil.readUnsignedShortLittleEndian(io);
				if (((Cursor)img).hotspotX > img.width) throw new EOFException("Invalid cursor hotspot X position");
				((Cursor)img).hotspotY = DataUtil.readUnsignedShortLittleEndian(io);
				if (((Cursor)img).hotspotY > img.height) throw new EOFException("Invalid cursor hotspot Y position");
			}
			img.dataSize = DataUtil.readUnsignedIntegerLittleEndian(io);
			img.dataOffset = DataUtil.readUnsignedIntegerLittleEndian(io);
			dir.images.add(img);
		}
		return dir;
	}
	
	public static OpenedDataCache<ImageDirectory> cache = new OpenedDataCache<ImageDirectory>(ImageDirectory.class, 5*60*1000) {

		@Override
		protected AsyncWork<ImageDirectory,Exception> open(IO.Readable io, WorkProgress progress, long work) {
			Task.Cpu<ImageDirectory, Exception> task = new Task.Cpu<ImageDirectory, Exception>("Read icon directory", Task.PRIORITY_NORMAL) {
				@Override
				public ImageDirectory run() throws Exception {
					ImageDirectory ico = readImageDirectory((IO.Readable.Buffered)io, null);
					if (progress != null) progress.progress(work);
					return ico;
				}
			};
			task.startOn(io.canStartReading(), false);
			return task.getOutput();
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(ImageDirectory ico) {
		}
		
	};
	
	public static class ICOImageProvider implements DIBImageProvider {
		public ICOImageProvider(int realHeight) {
			this.realHeight = realHeight;
		}
		private int realHeight;
		@Override
		public <T extends IO.Readable.Buffered & IO.Readable.Seekable> AsyncWork<BufferedImage, Exception> provide(T io) {
			DIBHeader header = new DIBHeader();
			AsyncWork<Integer, Exception> readHeader = DIBReader.readHeader(io, header, -1);
			AsyncWork<BufferedImage, Exception> result = new AsyncWork<>();
			readHeader.listenAsync(new Task.Cpu<Void, NoException>("Read ICO", io.getPriority()) {
				@Override
				public Void run() {
					if (readHeader.hasError()) { result.error(readHeader.getError()); return null; }
					if (readHeader.isCancelled()) { result.cancel(readHeader.getCancelEvent()); return null; }
					if (realHeight == -1 || header.height == 2 * realHeight) {
						// TODO
						/*
						Images with less than 32 bits of color depth follow a particular format: the image is encoded as a single image consisting of a color mask (the "XOR mask") together with an opacity mask (the "AND mask").[6] The XOR mask must precede the AND mask inside the bitmap data; if the image is stored in bottom-up order (which it most likely is), the XOR mask would be drawn below the AND mask. The AND mask is 1 bit per pixel, regardless of the color depth specified by the BMP header, and specifies which pixels are fully transparent and which are fully opaque. The XOR mask conforms to the bit depth specified in the BMP header and specifies the numerical color or palette value for each pixel. Together, the AND mask and XOR mask make for a non-transparent image representing an image with 1-bit transparency; they also allow for inversion of the background. The height for the image in the ICONDIRENTRY structure of the ICO/CUR file takes on that of the intended image dimensions (after the masks are composited), whereas the height in the BMP header takes on that of the two mask images combined (before they are composited). Therefore, the masks must each be of the same dimensions, and the height specified in the BMP header must be exactly twice the height specified in the ICONDIRENTRY structure.[citation needed]

						32-bit images (including 32-bit BITMAPINFOHEADER-format BMP images[Notes 1]) are specifically a 24-bit image with the addition of an 8-bit channel for alpha compositing. Thus, in 32-bit images, the AND mask is not required, but recommended for consideration. Windows XP and higher will use a 32-bit image in less than True color mode by constructing an AND mask based on the alpha channel (if one does not reside with the image already) if no 24-bit version of the image is supplied in the ICO/CUR file. However, earlier versions of Windows interpret all pixels with 100% opacity unless an AND mask is supplied with the image. Supplying a custom AND mask will also allow for tweaking and hinting by the icon author. Even if the AND mask is not supplied, if the image is in Windows BMP format, the BMP header must still specify a doubled height.							 
													 */
						header.height /= 2;
					}
					DIBReader.readBitmap(header, io).listenInline(result);
					return null;
				}
			}, true);
			return result;
		}
	}
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<CachedObject<ImageDirectory>,Exception> get = cache.open(data, this, Task.PRIORITY_IMPORTANT, null, 0);
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Create sub-data from ico file", Task.PRIORITY_IMPORTANT) {
			@Override
			public Void run() {
				CachedObject<ImageDirectory> cache = get.getResult();
				if (cache == null) {
					list.done();
					return null;
				}
				ImageDirectory dir = cache.get();
				int index = 1;
				ArrayList<Data> subdata = new ArrayList<>(dir.images.size());
				for (Image i : dir.images) {
					SubData sb = new SubData(data, i.dataOffset, i.dataSize, "Icon " + (index++));
					subdata.add(sb);
					sb.setProperty(IMAGE_PROPERTY, i);
					sb.setProperty(DIBReaderOp.DIBImageProvider.DATA_PROPERTY, new ICOImageProvider(i.height));
				}
				list.newElements(subdata);
				list.done();
				cache.release(ICOCURFormat.this);
				return null;
			}
		};
		task.startOn(get, true);
	}
	
	public static <T extends IO.Readable.Buffered & IO.Readable.Seekable> byte[] createBMPHeader(T dib, long dibSize, Image i) throws IOException {
		long dibHeaderSize = DataUtil.readUnsignedIntegerLittleEndian(dib);
		byte[] header = new byte[14 + (int)dibHeaderSize];
		header[0] = 'B';
		header[1] = 'M';
		DataUtil.writeUnsignedIntegerLittleEndian(header, 2, dibSize + 14);
		DataUtil.writeUnsignedIntegerLittleEndian(header, 14, dibHeaderSize);
		dib.read(header, 14 + 4, (int)dibHeaderSize - 4);
		long offset = 14;
		offset += dibHeaderSize;
		if (dibHeaderSize == 12) {
			DataUtil.writeUnsignedShortLittleEndian(header, 14 + 4 + 2, i.height);
			int bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(header, 14 + 4 + 2 + 2 + 2);
			if (bitsPerPixel < 24)
				offset += (1 << bitsPerPixel) * 3; // palette data size
		} else {
			DataUtil.writeUnsignedIntegerLittleEndian(header, 14 + 4 + 4, i.height);
			dib.skip(10);
			int bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(header, 28);
			long compression = DataUtil.readUnsignedIntegerLittleEndian(header, 30);
			if (compression == 3)
				offset += 12; // bit mask
			long colorsUsed = DataUtil.readUnsignedIntegerLittleEndian(header, 46);
			if (colorsUsed == 0 && bitsPerPixel < 16)
				colorsUsed = (1 << bitsPerPixel);
			offset += colorsUsed * 4; // palette data
		}
		DataUtil.writeUnsignedIntegerLittleEndian(header, 10, offset);
		return header;
	}
	
	@Override
	public Class<ICOImageInfo.CommonProperties> getSubDataCommonProperties() {
		return ICOImageInfo.CommonProperties.class;
	}

	@Override
	public ICOImageInfo.CommonProperties getSubDataCommonProperties(Data subData) {
		Image img = (Image)subData.getProperty(IMAGE_PROPERTY);
		if (img == null)
			return null;
		ICOImageInfo.CommonProperties p = new ICOImageInfo.CommonProperties();
		p.width = img.width;
		p.height = img.height;
		return p;
	}

	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		// TODO
		return new AsyncWork<>(null, null);
	}
	
	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		return null;
	}

	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}

}
