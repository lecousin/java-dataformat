package net.lecousin.dataformat.executable.windows.versioninfo;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class VersionInfoDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { VersionInfoDataFormat.instance };
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)6, new byte[] { 'V',0,'S',0,'_',0,'V',0,'E',0,'R',0,'S',0,'I',0,'O',0,'N',0,'_',0,'I',0,'N',0,'F',0,'O',0,0,0,0,0,(byte)0xBD,0x04,(byte)0xEF,(byte)0xFE })
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return VersionInfoDataFormat.instance;
	}
	
}
