package net.lecousin.dataformat.image.png.io;

import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import net.lecousin.compression.deflate.DeflateReadable;
import net.lecousin.dataformat.image.io.Adam7ScanLineReader;
import net.lecousin.dataformat.image.io.GammaCorrection;
import net.lecousin.dataformat.image.io.ImageReader;
import net.lecousin.dataformat.image.io.InvalidImage;
import net.lecousin.dataformat.image.io.ScanLineHandler;
import net.lecousin.dataformat.image.io.ScanLineReader;
import net.lecousin.dataformat.image.png.PNGDataFormat;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.FragmentedSubIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.io.SubIO;
import net.lecousin.framework.io.buffering.BufferedIO;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.buffering.TwoBuffersIO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.math.FragmentedRangeLong;

public class PNGReader {

	@SuppressWarnings("resource")
	public static <T extends IO.Readable.Seekable&IO.KnownSize> AsyncWork<BufferedImage,Exception> readFromSeekable(T io) throws IOException {
		if (io instanceof IO.Readable.Buffered)
			return readFromBuffered((IO.Readable.Seekable&IO.Readable.Buffered)io);
		long size;
		try { size = io.getSizeSync(); }
		catch (IOException e) { return new AsyncWork<>(null, e); }
		if (size <= 4096)
			return readFromBuffered(new TwoBuffersIO.DeterminedSize(io, (int)size, 0));
		if (size <= 4096+8192)
			return readFromBuffered(new TwoBuffersIO.DeterminedSize(io, 4096, (int)(size-4096)));
		if (size <= 8192+32768)
			return readFromBuffered(new TwoBuffersIO.DeterminedSize(io, 8192, (int)(size-8192)));
		return readFromBuffered(new BufferedIO(io, size, 4096, 65536, true));
	}
	
	public static <T extends IO.Readable.Seekable&IO.Readable.Buffered> AsyncWork<BufferedImage,Exception> readFromBuffered(T io) {
		Data<T> data = new Data<T>(io);
		io.canStartReading().listenAsync(new ReadFileHeader<T>(data), true);
		// TODO listen to cancel
		return data.done;
	}
	
	private static class Data<T extends IO.Readable.Seekable&IO.Readable.Buffered> {
		private Data(T io) {
			this.io = io;
		}
		private T io;
		private AsyncWork<BufferedImage,Exception> done = new AsyncWork<BufferedImage,Exception>();
		
		// chunk buffer
		private byte[] b8 = new byte[8];
		private ByteBuffer bb8 = ByteBuffer.wrap(b8);
		
		// IHDR
		private int width;
		private int height;
		private byte bitDepth;
		private byte colourType;
		private byte compressionMethod;
		private byte filterMethod;
		private byte interlaceMethod;
		
		// palette
		private byte[] palette = null;
		private AsyncWork<Integer, IOException> paletteLoading = null;
		
		// alpha
		private byte[] transparency = null;
		private AsyncWork<Integer, IOException> transparencyLoading = null;
		
		// gamma
		private byte[] gamma = null;
		
		// data
		private FragmentedRangeLong IDAT = new FragmentedRangeLong();
		private AsyncWork<ICC_Profile,Exception> ICCProfile = null;
		private boolean sRGB, IEND = false;
		
		private int bytesPerPixel;
		private int bitsPerPixel;
		private ScanLineHandler scanner;
		private Task<ImageReader,NoException> prepareBufferedImage;
	}
	
	private static class ReadFileHeader<T extends IO.Readable.Seekable&IO.Readable.Buffered> extends Task.Cpu<Void, NoException> {
		private ReadFileHeader(Data<T> data) {
			super("Start reading PNG data", data.io.getPriority());
			this.data = data;
		}
		private Data<T> data;
		@Override
		public Void run() {
			// IO is buffered, the beginning can be done without synchronization (supposed to be around 40 bytes)
			try {
				if (data.io.readFully(data.b8) != 8) {
					data.done.unblockError(new Exception("Not PNG format"));
					return null;
				}
				if ((data.b8[0]&0xFF) != 0x89 ||
					data.b8[1] != 0x50 ||
					data.b8[2] != 0x4E ||
					data.b8[3] != 0x47 ||
					data.b8[4] != 0x0D ||
					data.b8[5] != 0x0A ||
					data.b8[6] != 0x1A ||
					data.b8[7] != 0x0A
				) {
					data.done.unblockError(new Exception("Not PNG format"));
					return null;
				}
				if (data.io.readFully(data.b8) != 8) {
					data.done.unblockError(new Exception("Not PNG format"));
					return null;
				}
				long size = DataUtil.readUnsignedIntegerBigEndian(data.b8, 0);
				if (data.b8[4] != 'I' ||
					data.b8[5] != 'H' ||
					data.b8[6] != 'D' ||
					data.b8[7] != 'R'
				) {
					data.done.unblockError(new Exception("Invalid PNG format: first chunk in PNG must be a IHDR"));
					return null;
				}
				if (data.io.readFully(data.b8) != 8) {
					data.done.unblockError(new Exception("Not PNG format"));
					return null;
				}
				data.width = DataUtil.readIntegerBigEndian(data.b8, 0);
				data.height = DataUtil.readIntegerBigEndian(data.b8, 4);
				data.bitDepth = data.io.readByte();
				data.colourType = data.io.readByte();
				data.compressionMethod = data.io.readByte();
				data.filterMethod = data.io.readByte();
				data.interlaceMethod = data.io.readByte();
				readNextChunk(data, 8+4+4+size+4, null);
			} catch (Exception e) {
				data.done.unblockError(e);
			}
			return null;
		}
	}

	private static <T extends IO.Readable.Seekable&IO.Readable.Buffered> boolean readNextChunk(Data<T> data, long pos, ReadChunk<T> reader) {
		data.bb8.clear();
		AsyncWork<Integer, IOException> read = data.io.readFullyAsync(pos, data.bb8);
		if (read.isUnblocked() && read.isSuccessful()) {
			// do not start a task
			int nb = read.getResult().intValue();
			if (nb <= 0) {
				if (!data.IEND) {
					data.done.unblockError(new Exception("Unexpected end of PNG data at "+pos));
					return false;
				}
			} else if (nb < 8) {
				if (!data.IEND) {
					data.done.unblockError(new Exception("Unexpected end of PNG data at "+pos+" (+"+nb+")"));
					return false;
				}
			} else if (reader != null) {
				reader.pos = pos+8;
				return true;
			} else
				new ReadChunk<T>(data, pos+8).run();
			return false;
		}
		read.listenInline(new AsyncWorkListener<Integer, IOException>() {
			@Override
			public void ready(Integer result) {
				if (result.intValue() <= 0) {
					if (!data.IEND) {
						data.done.unblockError(new Exception("Unexpected end of PNG data at "+pos));
						return;
					}
				} else if (result.intValue() < 8) {
					if (!data.IEND) {
						data.done.unblockError(new Exception("Unexpected end of PNG data at "+pos+" (+"+result.intValue()+")"));
						return;
					}
				} else
					new ReadChunk<T>(data, pos+8).start();
			}
			@Override
			public void error(IOException error) {
				data.done.unblockError(error);
			}
			@Override
			public void cancelled(CancelException event) {
				data.done.unblockCancel(event);
			}
		});
		return false;
	}
	
	private static final int IDAT = ('I'<<24) | ('D'<<16) | ('A'<<8) | 'T';
	private static final int IEND = ('I'<<24) | ('E'<<16) | ('N'<<8) | 'D';
	private static final int PLTE = ('P'<<24) | ('L'<<16) | ('T'<<8) | 'E';
	private static final int iCCP = ('i'<<24) | ('C'<<16) | ('C'<<8) | 'P';
	private static final int sRGB = ('s'<<24) | ('R'<<16) | ('G'<<8) | 'B';
	private static final int sBIT = ('s'<<24) | ('B'<<16) | ('I'<<8) | 'T';
	private static final int cHRM = ('c'<<24) | ('H'<<16) | ('R'<<8) | 'M';
	private static final int pHYs = ('p'<<24) | ('H'<<16) | ('Y'<<8) | 's';
	private static final int sPLT = ('s'<<24) | ('P'<<16) | ('L'<<8) | 'T';
	private static final int tIME = ('t'<<24) | ('I'<<16) | ('M'<<8) | 'E';
	private static final int iTXt = ('i'<<24) | ('T'<<16) | ('X'<<8) | 't';
	private static final int tEXt = ('t'<<24) | ('E'<<16) | ('X'<<8) | 't';
	private static final int zTXt = ('z'<<24) | ('T'<<16) | ('X'<<8) | 't';
	private static final int bKGD = ('b'<<24) | ('K'<<16) | ('G'<<8) | 'D';
	private static final int hIST = ('h'<<24) | ('I'<<16) | ('S'<<8) | 'T';
	private static final int tRNS = ('t'<<24) | ('R'<<16) | ('N'<<8) | 'S';
	private static final int gAMA = ('g'<<24) | ('A'<<16) | ('M'<<8) | 'A';
	
	private static class ReadChunk<T extends IO.Readable.Seekable&IO.Readable.Buffered> extends Task.Cpu<Void, NoException> {
		private ReadChunk(Data<T> data, long pos) {
			super("Read chunk in PNG data", data.io.getPriority());
			this.data = data;
			this.pos = pos;
		}
		private Data<T> data;
		private long pos;
		@Override
		public Void run() {
			do {
				long size = DataUtil.readUnsignedIntegerBigEndian(data.b8, 0);
				int type = DataUtil.readIntegerBigEndian(data.b8, 4);
				switch (type) {
				case iCCP:
					if (data.sRGB) { data.done.unblockError(new Exception("Invalid PNG format: iCCP cannot be present together with sRGB")); return null; }
					if (data.palette != null) { data.done.unblockError(new Exception("Invalid PNG format: iCCP must be before PLTE")); return null; }
					if (data.ICCProfile != null) { data.done.unblockError(new Exception("Invalid PNG format: only one iCCP can be present")); return null; }
					data.ICCProfile = readICCProfile(data, pos, size);
					break;
				case sRGB:
					if (data.ICCProfile != null) { data.done.unblockError(new Exception("Invalid PNG format: sRGB cannot be present together with iCCP")); return null; }
					if (data.palette != null) { data.done.unblockError(new Exception("Invalid PNG format: sRGB must be before PLTE")); return null; }
					if (data.sRGB) { data.done.unblockError(new Exception("Invalid PNG format: only one sRGB can be present")); return null; }
					data.sRGB = true;
					break;
				case sBIT:
					// ignore
					//if (PLTE) { data.done.unblockError(new Exception("Invalid PNG format: sBIT must be before PLTE")); return null; }
					//if (sBIT) { data.done.unblockError(new Exception("Invalid PNG format: only one sBIT can be present")); return null; }
					break;
				case gAMA:
					if (data.palette != null) { data.done.unblockError(new Exception("Invalid PNG format: gAMA must be before PLTE")); return null; }
					if (data.gamma != null) { data.done.unblockError(new Exception("Invalid PNG format: only one gAMA can be present")); return null; }
					data.gamma = new byte[4];
					try { data.io.readFullySync(pos, ByteBuffer.wrap(data.gamma)); }
					catch (IOException e) { data.done.unblockError(e); return null; }
					break;
				case cHRM:
					// ignore
					//if (PLTE) { data.done.unblockError(new Exception("Invalid PNG format: cHRM must be before PLTE")); return null; }
					//if (cHRM) { data.done.unblockError(new Exception("Invalid PNG format: only one cHRM can be present")); return null; }
					break;
				case PLTE:
					// must be before first IDAT
					if (!data.IDAT.isEmpty()) { data.done.unblockError(new Exception("Invalid PNG format: PLTE must be before any IDAT")); return null; }
					if ((size % 3) != 0) { data.done.unblockError(new Exception("Invalid PNG format: palette size is not a multiple of 3")); return null; }
					if (size > 256*3) { data.done.unblockError(new Exception("Invalid PNG format: palette size greater than the maximum: "+size)); return null; }
					data.palette = new byte[(int)size];
					data.paletteLoading = data.io.readFullyAsync(pos, ByteBuffer.wrap(data.palette));
					break;
				case pHYs:
					// ignore
					// should be before IDAT
					break;
				case sPLT:
					// ignore
					// should be before IDAT
					break;
				case tIME:
					// ignore
					break;
				case iTXt:
					// ignore
					break;
				case tEXt:
					// ignore
					break;
				case zTXt:
					// ignore
					break;
				case bKGD:
					// ignore
					break;
				case hIST:
					// ignore
					break;
				case tRNS:
					data.transparency = new byte[(int)size];
					if (size < 32)
						try { data.io.readFullySync(pos, ByteBuffer.wrap(data.transparency)); }
						catch (IOException e) { data.done.unblockError(e); return null; }
					else
						data.transparencyLoading = data.io.readFullyAsync(pos, ByteBuffer.wrap(data.transparency));
					break;
				case IDAT:
					// when we have the first IDAT, we can prepare the reader
					if (data.prepareBufferedImage == null)
						if (!prepareBufferedImage(data)) return null;
					data.IDAT.addRange(pos, pos+size-1);
					break;
				case IEND:
					if (data.IEND) { data.done.unblockError(new Exception("Invalid PNG format: more than one IEND chunk")); return null; }
					if (data.IDAT.isEmpty()) { data.done.unblockError(new Exception("Invalid PNG format: no IDAT chunk before IEND")); return null; }
					data.IEND = true;
					// we won't have IDAT anymore, we can start uncompressing it
					IO.Readable io;
					if (data.IDAT.size() == 1)
						io = new SubIO.Readable.Seekable(data.io, data.IDAT.getFirst().min, data.IDAT.getFirst().getLength(), "PNG image data: "+data.io.getSourceDescription(), false);
					else
						io = new FragmentedSubIO.Readable(data.io, data.IDAT, false, "PNG image data: "+data.io.getSourceDescription());
					IO.Readable.Buffered idata = uncompress(io, data.compressionMethod, data.scanner.getBytesToReadPerLine(data.width, data.bitsPerPixel)*data.height);
					
					JoinPoint<Exception> jp = JoinPoint.fromSynchronizationPoints(idata.canStartReading(), data.prepareBufferedImage.getOutput());
					jp.listenInline(new Runnable() {
						@Override
						public void run() {
							if (data.prepareBufferedImage.getResult() == null) return;
							AsyncWork<BufferedImage,Exception> read = data.prepareBufferedImage.getResult().read(idata);
							read.listenInline(new AsyncWorkListener<BufferedImage, Exception>() {
								@Override
								public void ready(BufferedImage result) {
									// TODO apply ICC Profile if any
									data.done.unblockSuccess(result);
								}
								@Override
								public void error(Exception error) {
									data.done.unblockError(error);
								}
								@Override
								public void cancelled(CancelException event) {
									data.done.unblockCancel(event);
								}
							});
						}
					});
					break;
				default:
					if (PNGDataFormat.log.isDebugEnabled()) PNGDataFormat.log.debug("Unknown PNG chunk: "+((char)(type>>24))+((char)((type>>16)&0xFF))+((char)((type>>8)&0xFF))+((char)(type&0xFF)));
					break;
				}
				if (!readNextChunk(data, pos+size+4, this))
					break;
			} while (true);
			return null;
		}
	}
	
	private static <T extends IO.Readable.Seekable&IO.Readable.Buffered> boolean prepareBufferedImage(Data<T> data) {
		if (data.filterMethod == 0) {
			switch (data.colourType) {
			// Greyscale
			case 0:
				data.bytesPerPixel = data.bitDepth/8 + ((data.bitDepth%8) != 0 ? 1 : 0);
				data.bitsPerPixel = data.bitDepth;
				break;
			// True color
			case 2:
				data.bytesPerPixel = 3*data.bitDepth/8;
				data.bitsPerPixel = 3*data.bitDepth;
				break;
			// Indexed color
			case 3:
				data.bytesPerPixel = data.bitDepth/8 + ((data.bitDepth%8) != 0 ? 1 : 0);
				data.bitsPerPixel = data.bitDepth;
				break;
			// Greyscale with alpha
			case 4:
				data.bytesPerPixel = 2*data.bitDepth/8;
				data.bitsPerPixel = 2*data.bitDepth;
				break;
			// True color with alpha
			case 6:
				data.bytesPerPixel = 4*data.bitDepth/8;
				data.bitsPerPixel = 4*data.bitDepth;
				break;
			default:
				data.done.unblockError(new InvalidImage("Unknown PNG color type "+data.colourType));
				return false;
			}
			data.scanner = new ScanLineFilter0(data.bytesPerPixel);
		} else {
			data.done.unblockError(new Exception("Unknown filter method for PNG: "+data.filterMethod));
			return false;
		}
		
		ArrayList<ISynchronizationPoint<IOException>> toWait = new ArrayList<>(2);
		if (data.colourType != 3 && data.transparency != null && data.transparencyLoading != null && !data.transparencyLoading.isUnblocked())
			toWait.add(data.transparencyLoading);
		data.prepareBufferedImage = new Task.Cpu<ImageReader, NoException>("Prepare BufferedImage from PNG", data.io.getPriority()) {
			@Override
			public ImageReader run() {
				GammaCorrection gamma = null;
				if (data.gamma != null && !data.sRGB && data.ICCProfile == null)
					gamma = new GammaCorrection(((double)DataUtil.readUnsignedIntegerBigEndian(data.gamma, 0))/100000);
				try {
					ImageReader reader;
					switch (data.colourType) {
					case 0: { // Greyscale
						int transparentPixel = data.transparency != null ? DataUtil.readUnsignedShortBigEndian(data.transparency, 0) : -1;
						if (data.interlaceMethod == 0)
							reader = ScanLineReader.createGreyscale(data.width, data.height, data.bitDepth, transparentPixel, gamma, data.scanner, false, data.io.getPriority());
						else if (data.interlaceMethod == 1)
							reader = Adam7ScanLineReader.createGreyscale(data.width, data.height, data.bitDepth, transparentPixel, gamma, false, data.scanner, data.io.getPriority());
						else
							throw new InvalidImage("Unknown interlace method for PNG: "+data.interlaceMethod);
						break; }
					case 2: { // True color
						int transparentR = -1, transparentG = -1, transparentB = -1;
						if (data.transparency != null) {
							transparentR = DataUtil.readUnsignedShortBigEndian(data.transparency, 0);
							transparentG = DataUtil.readUnsignedShortBigEndian(data.transparency, 2);
							transparentB = DataUtil.readUnsignedShortBigEndian(data.transparency, 4);
						}
						if (data.interlaceMethod == 0)
							reader = ScanLineReader.createTrueColor(data.width, data.height, data.bitDepth, transparentR, transparentG, transparentB, gamma, data.scanner, false, data.io.getPriority());
						else if (data.interlaceMethod == 1)
							reader = Adam7ScanLineReader.createTrueColor(data.width, data.height, data.bitDepth, transparentR, transparentG, transparentB, gamma, false, data.scanner, data.io.getPriority());
						else
							throw new InvalidImage("Unknown interlace method for PNG: "+data.interlaceMethod);
						break; }
					case 3: { // Indexed color
						GammaCorrection _gamma = gamma;
						Task<IndexColorModel, Exception> createCM = new Task.Cpu<IndexColorModel, Exception>("Create IndexColorModel from PNG", data.io.getPriority()) {
							@Override
							public IndexColorModel run() {
								// TODO can improve by instantiating IndexColorModel with the palette data directly
								int nbEntries = data.palette.length/3;
								byte[] r = new byte[nbEntries];
								byte[] g = new byte[nbEntries];
								byte[] b = new byte[nbEntries];
								for (int i = 0; i < nbEntries; ++i) {
									r[i] = data.palette[i*3];
									g[i] = data.palette[i*3+1];
									b[i] = data.palette[i*3+2];
									if (_gamma != null) {
										r[i] = (byte)_gamma.modifyPixelSample(r[i]&0xFF, 8);
										g[i] = (byte)_gamma.modifyPixelSample(g[i]&0xFF, 8);
										b[i] = (byte)_gamma.modifyPixelSample(b[i]&0xFF, 8);
									}
								}
								byte[] a;
								if (data.transparency != null) {
									a = new byte[nbEntries];
									for (int i = 0; i < nbEntries; ++i)
										a[i] = i < data.transparency.length ? data.transparency[i] : (byte)255;
								} else
									a = null;
								return new IndexColorModel(data.bitDepth, r.length, r, g, b, a);
							}
						};
						createCM.startOn(false, data.paletteLoading, data.transparencyLoading);
						if (data.interlaceMethod == 0)
							reader = ScanLineReader.createIndexed(data.width, data.height, data.bitDepth, createCM.getOutput(), data.scanner);
						else if (data.interlaceMethod == 1)
							reader = Adam7ScanLineReader.createIndexed(data.width, data.height, data.bitDepth, createCM.getOutput(), false, data.scanner);
						else
							throw new InvalidImage("Unknown interlace method for PNG: "+data.interlaceMethod);
						break; }
					case 4: { // Greyscale with alpha
						if (data.interlaceMethod == 0)
							reader = ScanLineReader.createGreyscaleWithAlpha(data.width, data.height, data.bitDepth, gamma, data.scanner, false, data.io.getPriority());
						else if (data.interlaceMethod == 1)
							reader = Adam7ScanLineReader.createGreyscaleWithAlpha(data.width, data.height, data.bitDepth, gamma, false, data.scanner, data.io.getPriority());
						else
							throw new InvalidImage("Unknown interlace method for PNG: "+data.interlaceMethod);
						break; }
					case 6: { // True color with alpha
						if (data.interlaceMethod == 0)
							reader = ScanLineReader.createRGBA(data.width, data.height, data.bitDepth, gamma, data.scanner, false, data.io.getPriority());
						else if (data.interlaceMethod == 1)
							reader = Adam7ScanLineReader.createRGBA(data.width, data.height, data.bitDepth, gamma, false, data.scanner, data.io.getPriority());
						else
							throw new InvalidImage("Unknown interlace method for PNG: "+data.interlaceMethod);
						break; }
					default:
						throw new InvalidImage("Unknown PNG color type "+data.colourType);
					}
					return reader;
				} catch (Exception e) {
					data.done.unblockError(e);
					return null;
				}
			}
		};
		if (toWait.isEmpty())
			data.prepareBufferedImage.start();
		else
			JoinPoint.fromSynchronizationPoints(toWait).listenAsync(data.prepareBufferedImage, true);
		return true;
	}
	
	private static <T extends IO.Readable.Seekable&IO.Readable.Buffered> AsyncWork<ICC_Profile,Exception> readICCProfile(Data<T> data, long pos, long size) {
		AsyncWork<ICC_Profile,Exception> done = new AsyncWork<>();
		ByteBuffer b = ByteBuffer.allocate(81);
		AsyncWork<Integer,IOException> read = data.io.readFullyAsync(pos, b);
		read.listenAsync(new Task.Cpu<Void,NoException>("Read iCCP chunk in PNG", data.io.getPriority()) {
			@SuppressWarnings("resource")
			@Override
			public Void run() {
				if (read.isCancelled()) { done.unblockCancel(read.getCancelEvent()); return null; }
				if (!read.isSuccessful()) { done.unblockError(read.getError()); return null; }
				b.flip();
				int i;
				for (i = 0; b.hasRemaining(); ++i)
					if (b.get() == 0) break;
				if (!b.hasRemaining()) {
					done.unblockError(new Exception("Invalid PNG format: iCCP chunk is invalid"));
					return null;
				}
				byte comp = b.get();
				SubIO.Readable.Seekable subio = new SubIO.Readable.Seekable(data.io, pos+i+2, size-(i+2), "ICC Profile in PNG: "+data.io.getSourceDescription(), false);
				IO.Readable.Buffered io = uncompress(subio, comp, -1);
				if (io == null) {
					done.unblockError(new Exception("Compression method "+comp+" not supported in PNG format"));
					return null;
				}
				io.canStartReading().listenAsync(new Task.Cpu<Void, NoException>("Reading ICC Profile in PNG",data.io.getPriority()) {
					@Override
					public Void run() {
						InputStream in = IOAsInputStream.get(io, true);
						try {
							ICC_Profile profile = ICC_Profile.getInstance(in);
							done.unblockSuccess(profile);
						} catch (Exception e) {
							done.unblockError(e);
						}
						return null;
					}
				}, true);
				return null;
			}
		}, true);
		return done;
	}
	
	private static IO.Readable.Buffered uncompress(IO.Readable io, byte method, int expectedUncompressedSize) {
		switch (method) {
		case 0: return uncompressZlib(io, expectedUncompressedSize);
		default: return null;
		}
	}
	@SuppressWarnings("resource")
	private static IO.Readable.Buffered uncompressZlib(IO.Readable io, int expectedUncompressedSize) {
		DeflateReadable unc = new DeflateReadable(io, io.getPriority(), false);
		if (expectedUncompressedSize > 0) {
			if (expectedUncompressedSize < 5000)
				return new TwoBuffersIO(unc, 512, expectedUncompressedSize);
			if (expectedUncompressedSize < 16384)
				return new TwoBuffersIO(unc, 2048, expectedUncompressedSize);
			if (expectedUncompressedSize < 65536)
				return new TwoBuffersIO(unc, 4096, expectedUncompressedSize-3000);
			if (expectedUncompressedSize < 2048+8192*16)
				return new PreBufferedReadable(unc, 2048, io.getPriority(), 8192, io.getPriority(), (expectedUncompressedSize-2048)/8192+1);
			return new PreBufferedReadable(unc, 2048, io.getPriority(), 8192, io.getPriority(), 16);
		}
		return new PreBufferedReadable(unc, 1024, io.getPriority(), 4096, io.getPriority(), 32);
	}
	
	private static class ScanLineFilter0 implements ScanLineHandler {
		public ScanLineFilter0(int bytesPerPixel) {
			this.bytesPerPixel = bytesPerPixel;
		}
		
		private int bytesPerPixel;
		
		@Override
		public int getBytesToReadPerLine(int width, int bitsPerPixel) {
			int bits = width*bitsPerPixel;
			bits = bits/8 + ((bits%8) == 0 ? 0 : 1);
			return bits > 0 ? bits+1 : 0;
		}
		
		@Override
		public void reset() {
			prevLine = null;
		}
		
		private byte[] prevLine = null;
		
		@Override
		public void scan(byte[] line, byte[] output, int outputOffset) throws InvalidImage {
			byte type = line[0];
			switch (type) {
			case 0: System.arraycopy(line, 1, output, outputOffset, line.length-1); break;
			case 1: sub(line, output, outputOffset); break;
			case 2: up(line, output, outputOffset); break;
			case 3: avg(line, output, outputOffset); break;
			case 4: paeth(line, output, outputOffset); break;
			default: throw new InvalidImage("Invalid filter type in PNG: "+type);
			}
			if (prevLine == null)
				prevLine = new byte[line.length-1];
			System.arraycopy(output, outputOffset, prevLine, 0, line.length-1);
		}
		
		private void sub(byte[] line, byte[] out, int off) {
			int prev = -bytesPerPixel;
			for (int i = 1; i < line.length; ++i) {
				if (prev < 0) out[off+i-1] = line[i];
				else out[off+i-1] = (byte)(( (line[i]&0xFF) + (out[off+prev]&0xFF) )&0xFF);
				prev++;
			}
		}
		private void up(byte[] line, byte[] out, int off) {
			if (prevLine == null)
				System.arraycopy(line, 1, out, off, line.length-1);
			else {
				for (int i = 1; i < line.length; ++i)
					out[off+i-1] = (byte)(( (line[i]&0xFF) + (prevLine[i-1]&0xFF) )&0xFF);
			}
		}
		private void avg(byte[] line, byte[] out, int off) {
			int prev = -bytesPerPixel;
			for (int i = 1; i < line.length; ++i) {
				out[off+i-1] = (byte)(( (line[i]&0xFF) + ( (prev < 0 ? 0 : out[off+prev]&0xFF) + (prevLine != null ? prevLine[i-1]&0xFF : 0) )/2 )&0xFF);
				prev++;
			}
		}
		private void paeth(byte[] line, byte[] out, int off) {
			int prev = -bytesPerPixel;
			for (int i = 1; i < line.length; ++i) {
				out[off+i-1] = (byte)(( (line[i]&0xFF) + predictor(prev < 0 ? 0 : (out[off+prev]&0xFF), prevLine == null ? 0 : (prevLine[i-1]&0xFF), prevLine == null || prev < 0 ? 0 : (prevLine[prev]&0xFF)) )&0xFF);
				prev++;
			}
		}
		private static int predictor(int a, int b, int c) {
			int p = a + b - c;
			int pa = Math.abs(p - a);
			int pb = Math.abs(p - b);
			int pc = Math.abs(p - c);
			if (pa <= pb && pa <= pc) return a;
			if (pb <= pc) return b;
			return c;
		}
	}
}
