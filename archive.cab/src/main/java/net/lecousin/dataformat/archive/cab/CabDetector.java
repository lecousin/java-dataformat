package net.lecousin.dataformat.archive.cab;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class CabDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 'M', 'S', 'C', 'F' })
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			CabDataFormat.instance
		};
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return CabDataFormat.instance;
	}
	
}
