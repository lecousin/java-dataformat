package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class VDIDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0x40, new byte[] { 0x7F, 0x10, (byte)0xDA, (byte)0xBE })
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { VDIDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return VDIDataFormat.instance;
	}

}
