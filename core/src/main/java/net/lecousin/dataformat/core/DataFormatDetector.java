package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;

public interface DataFormatDetector {

	public static class Signature {
		public Signature(short pos, byte[] bytes) { this.pos = pos; this.bytes = bytes; }
		public short pos;
		public byte[] bytes;
	}
	
	public Signature[] getHeaderSignature();
	
	public DataFormat[] getDetectedFormats();
	
	public static interface OnlyHeaderNeeded extends DataFormatDetector {
		
		public abstract DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize);

	}
	
	public static interface MoreThanHeaderNeeded extends DataFormatDetector {

		public abstract AsyncSupplier<DataFormat,NoException> finishDetection(Data data, byte[] header, int headerLength, IO.Readable.Seekable io, long dataSize);
		
	}
	
}
