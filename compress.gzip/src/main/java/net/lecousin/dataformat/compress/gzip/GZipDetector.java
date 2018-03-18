package net.lecousin.dataformat.compress.gzip;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class GZipDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 0x1F, (byte)0x8B })	
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { GZipDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return GZipDataFormat.instance;
	}

}
