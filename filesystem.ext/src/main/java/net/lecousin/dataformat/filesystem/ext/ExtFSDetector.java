package net.lecousin.dataformat.filesystem.ext;

import java.nio.ByteBuffer;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;

public class ExtFSDetector implements DataFormatDetector.MoreThanHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return null;
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { ExtFSDataFormat.instance };
	}

	@Override
	public AsyncSupplier<DataFormat, NoException> finishDetection(Data data, byte[] header, int headerLength, IO.Readable.Seekable io, long dataSize) {
		if (dataSize != -1 && dataSize < 2048) return new AsyncSupplier<>(null, null);
		byte[] buf = new byte[2];
		AsyncSupplier<DataFormat, NoException> result = new AsyncSupplier<>();
		io.readFullyAsync(1024+0x38, ByteBuffer.wrap(buf)).onDone(
			() -> {
				if (buf[0] == 0x53 && buf[1] == (byte)0xEF)
					result.unblockSuccess(ExtFSDataFormat.instance);
				else
					result.unblockSuccess(null);
			}
		);
		return result;
	}

}
