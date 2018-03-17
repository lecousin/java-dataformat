package net.lecousin.dataformat.mbr;

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
		return MBRDataFormat.instance;
	}

}
