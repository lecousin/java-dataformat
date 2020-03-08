package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.DataBufferByte;
import java.awt.image.Raster;

import net.lecousin.dataformat.image.io.ImageReader;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;

public class RLE4Reader extends ImageReader {

	public RLE4Reader(DIBHeader header) {
		super(header.palette);
		this.header = header;
		int lineSize = header.width / 2;
		if ((header.width % 2) != 0) lineSize++;
		data = new byte[lineSize * header.height];
		raster = Raster.createPackedRaster(new DataBufferByte(data, data.length), header.width, header.height, 4, null);
	}

	private DIBHeader header;
	private byte[] data;
	
	@Override
	protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
		return Task.cpu("Read RLE4 bitmap", io.getPriority(), new Read(io)).start().getOutput();
	}
	
	private class Read implements Executable<Void, Exception> {
		private Read(IO.ReadableByteStream input) {
			this.input = input;
		}
		private IO.ReadableByteStream input;
		@Override
		public Void execute(Task<Void, Exception> task) throws Exception {
			int x = 0;
			int y = header.bottomUp ? header.height - 1 : 0;
			int lineSize = header.width / 2;
			if ((header.width % 2) != 0) lineSize++;
			int off = y * lineSize;
			boolean eol = false;
			do {
				int value = input.read();
				if (value < 0) break;
				if (value == 0) {
					value = input.read();
					if (value < 0) break;
					if (value == 0) {
						// end of scan line
						if (eol) { eol = false; continue; }
						x = 0;
						if (header.bottomUp) {
							if (--y < 0) break;
						} else {
							if (++y == header.height) break;
						}
						off = y * lineSize;
						continue;
					}
					eol = false;
					if (value == 1) {
						// end of data
						break;
					}
					if (value == 2) {
						// move forward
						int dx = input.read();
						int dy = input.read();
						if (dx < 0 || dy < 0) break;
						if (header.bottomUp) {
							y -= dy;
							if (y < 0) break;
						} else {
							y += dy;
							if (y >= header.height) break;
						}
						x += dx;
						if (x >= header.width) {
							eol = true;
							x = 0;
							if (header.bottomUp) {
								if (--y < 0) break;
							} else {
								if (++y == header.height) break;
							}
						}
						off = y * lineSize + x / 2;
						continue;
					}
					// unencoded data
					if ((x % 2) == 0) {
						int nb = value / 2;
						if ((value % 2) != 0) nb++;
						IOUtil.readFully(input, data, off, nb);
						// if the number of BYTES is odd, a 00 padding value follows
						if ((nb % 2) != 0) input.skip(1);
						x += value;
						off += value/2;
					} else {
						// here it is more complicated because we need to shift everything on 4 bits
						int nb = value / 2;
						if ((value % 2) != 0) nb++;
						boolean eof = false;
						for (int i = 0; i < nb; ++i) {
							int b = input.read();
							if (b < 0) { eof = true; break; }
							if (x >= header.width) continue;
							data[off] |= (b & 0xF0) >> 4;
							if (i == nb-1 && (value % 2) != 0) {
								x++;
								off++;
								break;
							}
							data[off + 1] = (byte)((b & 0x0F) << 4);
							x += 2;
							off++;
						}
						if (eof) break;
						// if the number of BYTES is odd, a 00 padding value follows
						if ((nb % 2) != 0) input.skip(1);
					}
					if (x >= header.width) {
						eol = true;
						x = 0;
						if (header.bottomUp) {
							if (--y < 0) break;
						} else {
							if (++y == header.height) break;
						}
						off = y * lineSize;
					}
					continue;
				}
				eol = false;
				int pixel = input.read();
				if (pixel < 0) break;
				if (x + value > header.width) value = header.width - x;
				for (int i = 0; i < value; ++i) {
					int px = (i % 2) == 0 ? (pixel & 0xF0) >> 4 : (pixel & 0x0F);
					if ((x % 2) == 0) {
						data[off] = (byte)(px << 4);
						x++;
					} else {
						data[off] |= (byte)px;
						x++;
						off++;
					}
				}
				if (x >= header.width) {
					eol = true;
					x = 0;
					if (header.bottomUp) {
						if (--y < 0) break;
					} else {
						if (++y == header.height) break;
					}
					off = y * lineSize;
				}
			} while (true);
			return null;
		}
	}
	
}
