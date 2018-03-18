package net.lecousin.dataformat.vm;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class VMDKDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 'K', 'D', 'M', 'V' })
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { VMDKDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return VMDKDataFormat.instance;
	}

}
