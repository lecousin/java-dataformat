package net.lecousin.dataformat.image.jpeg;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.log.Logger;

public class JPEGDataFormat extends ImageDataFormat {

	static Logger getLogger() {
		return LCCore.getApplication().getLoggerFactory().getLogger(JPEGDataFormat.class);
	}
	
	public static final JPEGDataFormat instance = new JPEGDataFormat();
	private JPEGDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("JPEG");
	}

	public static final String[] mime = new String[] { "image/jpeg", "image/jpg" };
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	public static final String[] extensions = new String[] { "jpg", "jpeg" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	@Override
	public AsyncSupplier<JPEGInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<?,IOException> open = data.openReadOnly(priority);
		if (open == null)
			return new AsyncSupplier<>(null,null);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Task<JPEGInfo,Exception> load = Task.cpu("Loading JPEG info", priority, new LoadInfo(open));
		load.startOn(open, false);
		return load.getOutput();
	}
	
	public static class LoadInfo<T extends IO.Readable.Seekable&IO.Readable.Buffered> implements Executable<JPEGInfo,Exception> {
		public LoadInfo(AsyncSupplier<T,Exception> open) {
			this.open = open;
		}
		private AsyncSupplier<T,Exception> open;
		private Logger logger = getLogger();
		@Override
		public JPEGInfo execute(Task<JPEGInfo,Exception> task) throws Exception {
			JPEGInfo info = new JPEGInfo();
			try (T io = open.getResult()) {
				io.skip(2);
				readNextMarker(io, info);
			} catch (IOException e) {
				if (logger.debug())
					logger.debug("Error parsing JPEG file", e);
				throw e;
			}
			return info;
		}

		private void readNextMarker(T io, JPEGInfo info) throws IOException {
			int size;
			int b;
			do {
				b = io.read();
				if (b < 0) return;
				if (b != 0xFF) {
					if (logger.debug())
						logger.debug("Unexpected value "+b+" in JPEG file at "+io.getPosition()+", expected is 255 in "+io.getSourceDescription());
					return;
				}
				b = io.read();
				if (b < 0) return;
				switch (b) {
				case 0xC0: case 0xC1: case 0xC2: case 0xC3: 
				case 0xC5: case 0xC6: case 0xC7: 
				case 0xC9: case 0xCA: case 0xCB: 
				case 0xCD: case 0xCE: case 0xCF: 
					readFrame(io, info);
					break;
				case 0xDA:
					size = DataUtil.Read16U.BE.read(io);
					io.skip(size-2);
					skipEntropyData(io);
					break;
				case 0x01:
				case 0xC4:
				case 0xCC:
				case 0xDB:
				case 0xDE:
					size = DataUtil.Read16U.BE.read(io);
					io.skip(size-2);
					break;
				case 0xDC:
					io.skip(4);
					break;
				case 0xDD:
					io.skip(2); // TODO check if 2 or 4
					break; 
				case 0xDF:
					io.skip(3);
					break;
				case 0xD0: case 0xD1: case 0xD2: case 0xD3: case 0xD4: case 0xD5: case 0xD6: case 0xD7: break;
				case 0xD9: return; // end of image
				case 0xE0: // JFIF
				case 0xE1: // EXIF
					size = DataUtil.Read16U.BE.read(io);
					io.skip(size-2);
					break;
				case 0xFE:
					readComment(io, info);
					break;
				default:
					if (logger.debug())
						logger.debug("Unknown JPEG Marker "+b+" at "+io.getPosition()+" in "+io.getSourceDescription());
					// let's do like we know this tag
					size = DataUtil.Read16U.BE.read(io);
					io.skip(size-2);
					break;
				}
			} while (true);
		}
		
		private void readFrame(T io, JPEGInfo info) throws IOException {
			int size = DataUtil.Read16U.BE.read(io);
			io.read();
			info.height = DataUtil.Read16U.BE.read(io);
			info.width = DataUtil.Read16U.BE.read(io);
			io.skip(size-2-1-2-2);
		}
		
		private void readComment(T io, JPEGInfo info) throws IOException {
			int size = DataUtil.Read16U.BE.read(io);
			byte[] b = new byte[size-2];
			io.readFully(b);
			info.comment = new String(b);
		}
		
		private void skipEntropyData(T io) throws IOException {
			do {
				int b = io.read();
				if (b < 0) break;
				if (b == 0xFF) {
					b = io.read();
					if (b != 0) {
						io.seekSync(SeekType.FROM_CURRENT, -2);
						return;
					}
				}
			} while (true);
		}
	}
	
}
