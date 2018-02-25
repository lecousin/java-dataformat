package net.lecousin.dataformat.archive.zip;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class ZipDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { ZipDataFormat.instance };
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 'P', 'K', 3, 4 })
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return ZipDataFormat.instance;
	}
	
}
