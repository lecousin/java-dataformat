package net.lecousin.dataformat.archive.coff;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class COFFArchiveDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { '!','<','a','r','c','h','>','\n'})
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { COFFArchiveDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return COFFArchiveDataFormat.instance;
	}

}
