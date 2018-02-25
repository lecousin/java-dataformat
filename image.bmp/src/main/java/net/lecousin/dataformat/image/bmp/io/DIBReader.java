package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import net.lecousin.dataformat.image.io.ImageReader;
import net.lecousin.dataformat.image.io.ScanLineReader;
import net.lecousin.dataformat.image.png.io.PNGReader;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.buffering.ReadableToSeekable;
import net.lecousin.framework.io.util.DataUtil;

public class DIBReader {

	public static AsyncWork<Integer, Exception> readHeader(IO.Readable input, DIBHeader header, long pixelsDataOffsetFromDIBHeader) {
		AsyncWork<Integer, Exception> result = new AsyncWork<>();
		byte[] b = new byte[4];
		AsyncWork<Integer, IOException> readHeaderSize = input.readFullyAsync(ByteBuffer.wrap(b));
		readHeaderSize.listenInline(new Runnable() {
			@Override
			public void run() {
				if (readHeaderSize.hasError()) { result.error(readHeaderSize.getError()); return; }
				if (readHeaderSize.isCancelled()) { result.cancel(readHeaderSize.getCancelEvent()); return; }
				if (readHeaderSize.getResult().intValue() != 4) { result.error(new Exception("Unexpected end of DIB data: only " + readHeaderSize.getResult().intValue() + " byte(s)")); return; }
				int size = (int)DataUtil.readUnsignedIntegerLittleEndian(b, 0);
				if (size < 12) { result.error(new Exception("Invalid DIB header size: " + size)); return; }
				if (size > 124) { result.error(new Exception("Unknown DIB version with header size = " + size)); return; }
				byte[] bh = new byte[size - 4];
				AsyncWork<Integer, IOException> readHeader = input.readFullyAsync(ByteBuffer.wrap(bh));
				readHeader.listenAsync(new Task.Cpu<Void, NoException>("Read DIB header from " + input.getSourceDescription(), input.getPriority()) {
					@Override
					public Void run() {
						int off;
						if (size == 12) {
							header.version = DIBHeader.Version.OS2_1;
							header.width = DataUtil.readShortLittleEndian(bh, 0);
							header.height = DataUtil.readShortLittleEndian(bh, 2);
							off = 4;
						} else {
							header.width = DataUtil.readIntegerLittleEndian(bh, 0);
							header.height = DataUtil.readIntegerLittleEndian(bh, 4);
							off = 8;
						}
						header.planes = DataUtil.readUnsignedShortLittleEndian(bh, off);
						off += 2;
						// planes is supposed to be 1, but do not throw an error if not...
						header.bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(bh, off);
						off += 2;
						
						// end of mandatory fields, now it depends on the header size
						
						if (size <= 64) {
							if (size == 40)
								header.version = DIBHeader.Version.WIN_3;
							else if (size == 52)
								header.version = DIBHeader.Version.WIN_3_PHOTOSHOP1;
							else if (size == 56)
								header.version = DIBHeader.Version.WIN_3_PHOTOSHOP2;
							else
								header.version = DIBHeader.Version.OS2_2;
						} else if (size == 108)
							header.version = DIBHeader.Version.WIN_4;
						else if (size == 124)
							header.version = DIBHeader.Version.WIN_5;
						
						// common part between Windows 3+ and OS/2 2.x: 40 bytes, possibly reduced by assuming a value of 0 for omitted fields
						
						if (off < size - 4) {
							header.compression = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
							off += 4;
						}
						if (off < size - 4) {
							header.bitmapSize = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
							off += 4;
						}
						if (off < size - 4) {
							header.horizRes = DataUtil.readIntegerLittleEndian(bh, off);
							off += 4;
						}
						if (off < size - 4) {
							header.vertRes = DataUtil.readIntegerLittleEndian(bh, off);
							off += 4;
						}
						if (off < size - 4) {
							header.colorsUsed = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
							off += 4;
						}
						if (off < size - 4) {
							header.importantColorsUsed = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
							off += 4;
						}
						
						if (size > 40 && size <= 64 && size != 52 && size !=56) {
							// specific to OS/2 2.x
							
							if (off < size - 4) {
								header.resUnit = DataUtil.readUnsignedShortLittleEndian(bh, off);
								off += 2;
							}
							if (off < size - 4)
								off += 2; // 2 bytes reserved
							if (off < size - 4) {
								header.recording = DataUtil.readUnsignedShortLittleEndian(bh, off);
								off += 2;
							}
							if (off < size - 4) {
								header.rendering = DataUtil.readUnsignedShortLittleEndian(bh, off);
								off += 2;
							}
							if (off < size - 4) {
								header.renderingSize1 = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							if (off < size - 4) {
								header.renderingSize2 = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							if (off < size - 4) {
								header.colorEncoding = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							if (off < size - 4) {
								header.appIdentifier = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							
						} else {
							if (size < 52 && (header.compression == 3 || header.compression == 6)) {
								// read 12 more bytes for bit fields
								int nb;
								try { nb = input.readFullySync(ByteBuffer.wrap(bh, 0, header.compression == 6 ? 16 : 12)); }
								catch (IOException e) {
									result.error(e);
									return null;
								}
								if (nb != (header.compression == 6 ? 16 : 12)) {
									result.error(new Exception("Cannot read bit fields: only " + nb + " byte(s) read, expected is " + (header.compression == 6 ? 16 : 12)));
									return null;
								}
								header.redMask = DataUtil.readUnsignedIntegerLittleEndian(bh, 0);
								header.greenMask = DataUtil.readUnsignedIntegerLittleEndian(bh, 4);
								header.blueMask = DataUtil.readUnsignedIntegerLittleEndian(bh, 8);
								off += 12;
								if (header.compression == 6) {
									header.alphaMask = DataUtil.readUnsignedIntegerLittleEndian(bh, 12);
									off += 4;
								}
							} else if (size >= 52) {
								header.redMask = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
								header.greenMask = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
								header.blueMask = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							if (size >= 56) {
								header.alphaMask = DataUtil.readUnsignedIntegerLittleEndian(bh, off);
								off += 4;
							}
							if (size >= 60) {
								// TODO continue for headers of 108 or 124 (Windows 4 or 5)
								off = size - 4;
							}
						}

						if (header.height < 0) {
							header.bottomUp = false;
							header.height = -header.height;
						}
						// try to read invalid width
						if (header.width < 0) header.width = -header.width;
						
						off += 4; // add header size => offset from DIB header
						
						// read palette
						if (size == 12) {
							if (header.bitsPerPixel <= 8) {
								int nbEntries = 1 << header.bitsPerPixel;
								if (pixelsDataOffsetFromDIBHeader > 0) {
									// if we know the position of the bitmap, we should calculate the palette size
									int realSize = (int)(pixelsDataOffsetFromDIBHeader - 12) / 3;
									if (realSize < nbEntries)
										nbEntries = realSize;
								}
								byte[] data = new byte[nbEntries * 3];
								AsyncWork<Integer, IOException> readPalette = input.readFullyAsync(ByteBuffer.wrap(data));
								int nb = nbEntries;
								int offset = off + data.length;
								Task.Cpu<IndexColorModel, Exception> createCM = new Task.Cpu<IndexColorModel, Exception>("Create IndexColorModel", input.getPriority()) {
									@Override
									public IndexColorModel run() {
										byte[] r = new byte[nb];
										byte[] g = new byte[nb];
										byte[] b = new byte[nb];
										for (int i = 0; i < nb; ++i) {
											int o = i*3;
											b[i] = data[o];
											g[i] = data[o+1];
											r[i] = data[o+2];
										}
										// handle oversized palette, which is rejected by the color model
										int size = nb > (1 << header.bitsPerPixel) ? 1 << header.bitsPerPixel : nb;
										return new IndexColorModel(header.bitsPerPixel, size, r, g, b);
									}
								};
								header.palette = createCM.getOutput();
								readPalette.listenInline(new Runnable() {
									@Override
									public void run() {
										if (readPalette.hasError()) { result.error(readPalette.getError()); return; }
										if (readPalette.isCancelled()) { result.cancel(readPalette.getCancelEvent()); return; }
										if (readPalette.getResult().intValue() != data.length) {
											result.error(new Exception("Only " + readPalette.getResult().intValue() + " byte(s) of palette read, expected is " + data.length));
											return;
										}
										result.unblockSuccess(Integer.valueOf(offset));
										createCM.start();
									}
								});
								return null;
							}
							// no palette
							result.unblockSuccess(Integer.valueOf(off));
							return null;
						}
						if (header.bitsPerPixel < 16 && header.bitsPerPixel > 0) {
							int nbEntries = (int)header.colorsUsed;
							if (nbEntries == 0)
								nbEntries = 1 << header.bitsPerPixel;
							if (pixelsDataOffsetFromDIBHeader > 0) {
								// if we know where the data starts, we can set a maximum number of entries to avoid invalid value
								int max = (int)((pixelsDataOffsetFromDIBHeader - off) / 4);
								if (nbEntries > max)
									nbEntries = max;
							}
							if (nbEntries > 16*1024*1024) { // avoid out of memory when invalid
								result.error(new Exception("Invalid number of palette entries: " + nbEntries));
								return null;
							}
							byte[] data = new byte[nbEntries * 4];
							AsyncWork<Integer, IOException> readPalette = input.readFullyAsync(ByteBuffer.wrap(data));
							int nb = nbEntries;
							int offset = off + data.length;
							Task.Cpu<IndexColorModel, Exception> createCM = new Task.Cpu<IndexColorModel, Exception>("Create IndexColorModel", input.getPriority()) {
								@Override
								public IndexColorModel run() {
									byte[] r = new byte[nb];
									byte[] g = new byte[nb];
									byte[] b = new byte[nb];
									for (int i = 0; i < nb; ++i) {
										int o = i*4;
										b[i] = data[o];
										g[i] = data[o+1];
										r[i] = data[o+2];
									}
									// handle oversized palette, which is rejected by the color model
									int size = nb > (1 << header.bitsPerPixel) ? 1 << header.bitsPerPixel : nb;
									return new IndexColorModel(header.bitsPerPixel, size, r, g, b);
								}
							};
							header.palette = createCM.getOutput();
							readPalette.listenInline(new Runnable() {
								@Override
								public void run() {
									if (readPalette.hasError()) { result.error(readPalette.getError()); return; }
									if (readPalette.isCancelled()) { result.cancel(readPalette.getCancelEvent()); return; }
									if (readPalette.getResult().intValue() != data.length) {
										result.error(new Exception("Only " + readPalette.getResult().intValue() + " byte(s) of palette read, expected is " + data.length));
										return;
									}
									result.unblockSuccess(Integer.valueOf(offset));
									createCM.start();
								}
							});
							return null;
						}
						
						if (header.compression == 0 && size < 52) {
							if (header.bitsPerPixel == 16) {
								header.redMask = 0x7C00;
								header.greenMask = 0x3E0;
								header.blueMask = 0x1F;
							} else if (header.bitsPerPixel == 32) {
								header.redMask   = 0x00FF0000;
								header.greenMask = 0x0000FF00;
								header.blueMask  = 0x000000FF;
							}
						}
						
						result.unblockSuccess(Integer.valueOf(off));
						
						return null;
					}
				}, true);
			}
		});
		return result;
	}
	
	@SuppressWarnings("resource")
	public static AsyncWork<BufferedImage,Exception> readBitmap(DIBHeader header, IO.Readable input) {
		int bytesPerLine = header.width * header.bitsPerPixel;
		if ((bytesPerLine % 8) == 0) bytesPerLine = bytesPerLine / 8;
		else bytesPerLine = bytesPerLine / 8 + 1;
		if ((bytesPerLine % 4) > 0)
			bytesPerLine += 4 - (bytesPerLine % 4);

		ImageReader reader = null;
		try {
			if (header.compression == 0) {
				// no compression, RGB format
				if (header.palette != null)
					reader = ScanLineReader.createIndexed(header.width, header.height, header.bitsPerPixel, bytesPerLine, header.bottomUp, header.palette);
				else if (header.bitsPerPixel == 16)
					reader = ScanLineReader.createTrueColor16Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
				else if (header.bitsPerPixel == 32)
					reader = ScanLineReader.createTrueColor32Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
				else if (header.bitsPerPixel == 24)
					reader = ScanLineReader.createTrueColor24Bits(header.width, header.height, bytesPerLine, header.bottomUp, new int[] { 2, 1, 0 }, input.getPriority());
			} else if (header.compression == 1) {
				// RLE8
				if (header.bitsPerPixel != 8)
					return new AsyncWork<>(null, new Exception("Invalid DIB: RLE8 compression specified, but " + header.bitsPerPixel + " bits per pixel specified while only 8 bits is allowed"));
				reader = new RLE8Reader(header);
			} else if (header.compression == 2) {
				// RLE4
				if (header.bitsPerPixel != 4)
					return new AsyncWork<>(null, new Exception("Invalid DIB: RLE4 compression specified, but " + header.bitsPerPixel + " bits per pixel specified while only 4 bits is allowed"));
				reader = new RLE4Reader(header);
			} else if (header.compression == 3) {
				if (!DIBHeader.Version.OS2_2.equals(header.version) || header.bitsPerPixel != 1) {
					if (header.bitsPerPixel == 16)
						reader = ScanLineReader.createTrueColor16Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
					else if (header.bitsPerPixel == 32)
						reader = ScanLineReader.createTrueColor32Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
					else if (header.bitsPerPixel == 24)
						reader = ScanLineReader.createTrueColor24Bits(header.width, header.height, bytesPerLine, header.bottomUp, new int[] { 2, 1, 0 }, input.getPriority());
				} else {
					// TODO Huffman 1D
				}
			} else if (header.compression == 4) {
				if (!DIBHeader.Version.OS2_2.equals(header.version) || header.bitsPerPixel != 24) {
					// JPEG
					// TODO use our own reader
					Task.Cpu<BufferedImage, Exception> task = new Task.Cpu<BufferedImage, Exception>("Read JPEG inside BMP", input.getPriority()) {
						@Override
						public BufferedImage run() throws Exception {
							SubIO.Readable sub = new SubIO.Readable(input, header.bitmapSize, "JPEG inside BMP: " + input.getSourceDescription(), false);
							return ImageIO.read(IOAsInputStream.get(sub));
						}
					};
					task.start();
					return task.getOutput();
				} else {
					// TODO 24-bit RLE
				}
			} else if (header.compression == 5) {
				// PNG
				// PNG needs a seekable input
				if (input instanceof IO.Readable.Seekable) {
					if (input instanceof IO.Readable.Buffered) {
						SubIO.Readable.Seekable.Buffered sub = new SubIO.Readable.Seekable.Buffered((IO.Readable.Seekable & IO.Readable.Buffered)input, ((IO.Readable.Seekable)input).getPosition(), header.bitmapSize, "PNG inside BMP: " + input.getSourceDescription(), false);
						return PNGReader.readFromBuffered(sub);
					}
					SubIO.Readable.Seekable sub = new SubIO.Readable.Seekable((IO.Readable.Seekable)input, ((IO.Readable.Seekable)input).getPosition(), header.bitmapSize, "PNG inside BMP: " + input.getSourceDescription(), false);
					return PNGReader.readFromSeekable(sub);
				}
				if (header.bitmapSize < 16 * 1024 * 1024) {
					byte[] data = new byte[(int)header.bitmapSize];
					AsyncWork<Integer, IOException> read = input.readFullyAsync(ByteBuffer.wrap(data));
					AsyncWork<BufferedImage, Exception> result = new AsyncWork<>();
					read.listenAsync(new Task.Cpu<Void, NoException>("Read PNG inside BMP", input.getPriority()) {
						@Override
						public Void run() {
							if (read.hasError()) { result.error(read.getError()); return null; }
							if (read.isCancelled()) { result.cancel(read.getCancelEvent()); return null; }
							int nb = read.getResult().intValue();
							ByteArrayIO io = new ByteArrayIO(data, nb, "PNG inside BMP from " + input.getSourceDescription());
							PNGReader.readFromBuffered(io).listenInline(result);
							return null;
						}
					}, true);
					return result;
				}
				SubIO.Readable sub = new SubIO.Readable(input, header.bitmapSize, "PNG inside BMP: " + input.getSourceDescription(), false);
				ReadableToSeekable io = new ReadableToSeekable(sub, 128 * 1024);
				return PNGReader.readFromBuffered(io);
			} else if (header.compression == 6) {
				if (header.bitsPerPixel == 16)
					reader = ScanLineReader.createTrueColor16Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
				else if (header.bitsPerPixel == 32)
					reader = ScanLineReader.createTrueColor32Bits(header.width, header.height, bytesPerLine, (int)header.redMask, (int)header.greenMask, (int)header.blueMask, (int)header.alphaMask, true, header.bottomUp, input.getPriority());
				else if (header.bitsPerPixel == 24)
					reader = ScanLineReader.createTrueColor24Bits(header.width, header.height, bytesPerLine, header.bottomUp, new int[] { 2, 1, 0 }, input.getPriority());
			}
			// TODO compression 11, 12, 13
		} catch (Exception e) {
			return new AsyncWork<>(null, e);
		}
		
		if (reader == null)
			return new AsyncWork<>(null, new Exception("Unknown DIB fomat: " + header.version + " compression " + header.compression + " " + header.bitsPerPixel + " bits per pixel"));
		
		IO.ReadableByteStream in;
		if (input instanceof IO.ReadableByteStream)
			in = (IO.ReadableByteStream)input;
		else
			in = new PreBufferedReadable(input, bytesPerLine, input.getPriority(), bytesPerLine * 2, input.getPriority(), header.height > 256 ? 128 : header.height > 2 ? header.height / 2 + 1 : 2);

		return reader.read(in);
	}
	
}
