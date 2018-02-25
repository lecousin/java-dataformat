package net.lecousin.dataformat.image.ico;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class ICODetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { ICODataFormat.instance };
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 0, 0 })
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < 6) return null;
		if (header[3] != 0) return null;
		if (header[2] == 1)
			return ICODataFormat.instance;
		if (header[2] == 2)
			return CURDataFormat.instance;
		return null;
	}
	
}
