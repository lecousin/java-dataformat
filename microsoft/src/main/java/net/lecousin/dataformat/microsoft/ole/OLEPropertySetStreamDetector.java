package net.lecousin.dataformat.microsoft.ole;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class OLEPropertySetStreamDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { (byte)0xFE, (byte)0xFF })
		};
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			OLEPropertySetStream.instance
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < 0x30) return null;
		if (header[3] != 0) return null;
		if (header[0x19] != 0) return null;
		if (header[0x1A] != 0) return null;
		int nbSets = DataUtil.readUnsignedShortLittleEndian(header, 0x18);
		if (nbSets == 0) return null;
		// check offset of first property set
		long offset = DataUtil.readUnsignedIntegerLittleEndian(header, 0x2C);
		if (offset != 0x30 + (nbSets - 1)*20) return null;
		return OLEPropertySetStream.instance;
	}
	
}
