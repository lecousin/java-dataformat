package net.lecousin.dataformat.image.jpeg;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class JPEGDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF })
		};
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			JPEGDataFormat.instance
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		int length = DataUtil.readUnsignedShortBigEndian(header, 4);
		if (4+length < headerLength)
			if ((header[4+length]&0xFF) != 0xFF)
				return null;
		return JPEGDataFormat.instance;
	}
	
}
