package net.lecousin.dataformat.executable.windows.msdos;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class MZDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			MZDataFormat.instance	
		};
	}

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 'M', 'Z' })
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < 0x1C)
			return null;
		int lastBlockSize = DataUtil.Read16U.LE.read(header, 0x02);
		if (lastBlockSize > 511)
			return null;
		int nbBlocks = DataUtil.Read16U.LE.read(header, 0x04);
		int dataOffset = nbBlocks*512;
		if (lastBlockSize != 0)
			dataOffset = dataOffset-512+lastBlockSize;
		if (dataSize != -1 && dataOffset > dataSize)
			return null;
		data.setProperty("MZDataOffset", new Integer(dataOffset));
		return MZDataFormat.instance;
	}
	
}
