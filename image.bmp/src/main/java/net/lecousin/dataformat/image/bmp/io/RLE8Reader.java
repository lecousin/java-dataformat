package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.DataBufferByte;
import java.awt.image.Raster;

import net.lecousin.dataformat.image.io.ImageReader;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;

public class RLE8Reader extends ImageReader {

	public RLE8Reader(DIBHeader header) {
		super(header.palette);
		this.header = header;
		data = new byte[header.width * header.height];
		raster = Raster.createInterleavedRaster(new DataBufferByte(data, data.length), header.width, header.height, header.width, 1, new int[] { 0 }, null);
	}

	private DIBHeader header;
	private byte[] data;
	
	@Override
	protected IAsync<Exception> readImageData(IO.ReadableByteStream io) {
		return Task.cpu("Read RLE8 bitmap", io.getPriority(), new Read(io)).start().getOutput();
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
			int off = y * header.width;
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
						off = y * header.width;
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
						off = y * header.width + x;
						continue;
					}
					// unencoded data
					IOUtil.readFully(input, data, off, value);
					// if the number of pixels is odd, a 00 padding value follows
					if ((value % 2) != 0) input.skip(1);
					x += value;
					off += value;
					if (x >= header.width) {
						eol = true;
						x = 0;
						if (header.bottomUp) {
							if (--y < 0) break;
						} else {
							if (++y == header.height) break;
						}
						off = y * header.width;
					}
					continue;
				}
				eol = false;
				int pixel = input.read();
				if (pixel < 0) break;
				byte px = (byte)pixel;
				if (x + value > header.width) value = header.width - x;
				for (int i = 0; i < value; ++i)
					data[off + i] = px;
				if (x + value == header.width) {
					eol = true;
					x = 0;
					if (header.bottomUp) {
						if (--y < 0) break;
					} else {
						if (++y == header.height) break;
					}
					off = y * header.width;
				} else {
					x += value;
					off += value;
				}
			} while (true);
			return null;
		}
	}
	
}
