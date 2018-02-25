package net.lecousin.dataformat.image.bmp;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class DIBDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)1, new byte[] { 0, 0, 0 }),
		};
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			DIBDataFormat.instance
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < (header[0] & 0xFF)) return null;
		if (header[0] == 12) {
			if (header[8] != 1 || header[9] != 0) return null;
		} else {
			if (header[0] < 40) return null;
			if (header[12] != 1 || header[13] != 0) return null;
		}
		return DIBDataFormat.instance;
	}
	
}
