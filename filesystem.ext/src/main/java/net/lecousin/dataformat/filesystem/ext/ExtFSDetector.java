package net.lecousin.dataformat.filesystem.ext;

import java.nio.ByteBuffer;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	public AsyncWork<DataFormat, NoException> finishDetection(Data data, byte[] header, int headerLength, IO.Readable.Seekable io, long dataSize) {
		if (dataSize != -1 && dataSize < 2048) return new AsyncWork<>(null, null);
		byte[] buf = new byte[2];
		AsyncWork<DataFormat, NoException> result = new AsyncWork<>();
		io.readFullyAsync(1024+0x38, ByteBuffer.wrap(buf)).listenInline(
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
