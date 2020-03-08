package net.lecousin.dataformat.image.bmp;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class BMPDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 'B', 'M' })
		};
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			BMPDataFormat.instance
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < 26)
			return null;
		if (dataSize > 0) {
			long bmpSize = DataUtil.Read32U.LE.read(header, 2);
			if (dataSize < bmpSize) return null;
		}
		// bmp header must be followed by a DIB header
		if (header[15] != 0 || header[16] != 0 || header[17] != 0)
			return null;
		if (header[14] == 12) {
			if (header[14 + 8] != 1 || header[14 + 9] != 0)
				return null;
			return BMPDataFormat.instance;
		} else if (header[14] == 40 || header[14] == 52 || header[14] == 56 || header[14] == 64 || header[14] == 108 || header[14] == 124) {
			if (header[14 + 12] != 1 || header[14 + 13] != 0)
				return null;
			return BMPDataFormat.instance;
		}
		return null;
	}
	
}
