package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class MBRDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)510, new byte[] { 0x55, (byte)0xAA })	
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { MBRDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		for (int i = 0; i < 4; ++i) {
			byte b = header[0x1BE + i * 16 + 0];
			if (b != 0 && b != (byte)0x80)
				return null;
		}
		return MBRDataFormat.instance;
	}

}
