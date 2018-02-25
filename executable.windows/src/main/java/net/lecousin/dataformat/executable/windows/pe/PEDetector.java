package net.lecousin.dataformat.executable.windows.pe;

import java.nio.ByteBuffer;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.executable.windows.msdos.MZDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;

public class PEDetector implements DataFormatDetector.OnlyHeaderNeeded {

	public static class InsideMZ implements DataFormatSpecializationDetector {
		@Override
		public DataFormat getBaseFormat() { return MZDataFormat.instance; }
		@Override
		public DataFormat[] getDetectedFormats() {
			return new DataFormat[] { PEDataFormat.instance };
		}
		@Override
		public AsyncWork<DataFormat, NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize) {
			if (headerSize < 0x40) return new AsyncWork<>(null,null);
			long pos = DataUtil.readUnsignedIntegerLittleEndian(header, 0x3C);
			if (pos+4 <= headerSize) {
				if (header[(int)pos] == 'P' && header[(int)(pos+1)] == 'E' && header[(int)(pos+2)] == 0 && header[(int)(pos+3)] == 0) {
					data.setProperty("PEOffset", new Long(pos));
					return new AsyncWork<>(PEDataFormat.instance, null);
				}
				return new AsyncWork<>(null, null);
			}
			AsyncWork<DataFormat, NoException> result = new AsyncWork<>();
			AsyncWork<? extends IO.Readable.Seekable,Exception> open = data.open(Task.PRIORITY_NORMAL);
			open.listenInline(new Runnable() {
				@Override
				public void run() {
					if (!open.isSuccessful()) {
						result.unblockSuccess(null);
						return;
					}
					@SuppressWarnings("resource")
					IO.Readable.Seekable io = open.getResult();
					ByteBuffer buffer = ByteBuffer.allocate(4);
					io.readAsync(pos, buffer).listenInline(new Runnable() {
						@Override
						public void run() {
							if (buffer.hasRemaining()) {
								result.unblockSuccess(null);
							} else {
								buffer.flip();
								if (buffer.get() == 'P' && buffer.get() == 'E' && buffer.get() == 0 && buffer.get() == 0) {
									data.setProperty("PEOffset", new Long(pos));
									result.unblockSuccess(PEDataFormat.instance);
								} else {
									result.unblockSuccess(null);
								}
							}
							io.closeAsync();
						}
					});
				}
			});
			return result;
		}
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { PEDataFormat.instance };
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] { new Signature((short)0, new byte[] { 'P', 'E', 0, 0 }) };
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		data.setProperty("PEOffset", new Long(0));
		return PEDataFormat.instance;
	}
	
}
