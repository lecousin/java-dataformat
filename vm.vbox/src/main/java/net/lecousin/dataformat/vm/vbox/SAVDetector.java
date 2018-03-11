package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class SAVDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 0x7F, 'V','i','r','t','u','a','l','B','o','x',' ','S','a','v','e','d','S','t','a','t','e',' ' })
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { SAVDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return SAVDataFormat.instance;
	}

}
