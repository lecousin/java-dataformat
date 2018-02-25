package net.lecousin.dataformat.archive.cfb;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class CFBDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { (byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0, (byte)0xA1, (byte)0xB1, 0x1A, (byte)0xE1 })	
		};
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			CFBDataFormat.instance
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return CFBDataFormat.instance;
	}
	
}
