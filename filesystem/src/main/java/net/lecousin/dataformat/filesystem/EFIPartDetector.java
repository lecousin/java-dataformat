package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class EFIPartDetector implements DataFormatDetector.OnlyHeaderNeeded {

	public static final byte[] EFI_HEADER_SIGNATURE = new byte[] { 'E', 'F', 'I', ' ', 'P', 'A', 'R', 'T' };

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, EFI_HEADER_SIGNATURE)
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { EFIPartDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return EFIPartDataFormat.instance;
	}
	
}
