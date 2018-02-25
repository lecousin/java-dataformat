package net.lecousin.dataformat.archive.tar;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class TarDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			TarDataFormat.instance
		};
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		// no specific signature
		return null;
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		// each tar entry is precedeed by a 512 bytes header
		if (headerLength < 512) return null;
		// numeric fields are ended with a null character, in some variants it may be a space
		if (header[100+7] != 0 && header[100+7] != 0x20) return null;
		if (header[108+7] != 0 && header[108+7] != 0x20) return null;
		if (header[116+7] != 0 && header[116+7] != 0x20) return null;
		if (header[124+11] != 0 && header[124+11] != 0x20) return null;
		if (header[136+11] != 0 && header[136+11] != 0x20) return null;
		if (header[148+7] != 0 && header[148+7] != 0x20) return null;
		// it cannot start with a null character
		if (header[0] == 0) return null;
		// first is the filename on 100 bytes, padded with zeros, and which cannot contain characters * and ?
		boolean zero = false;
		for (int i = 0; i < 100; ++i) {
			if (header[i] == 0) { zero = true; continue; }
			if (zero) return null;
			if (header[i] == '*' || header[i] == '?') return null;
		}
		// check numeric fields in octal base
		for (int i = 124; i < 124+11; ++i)
			if (header[i] < '0' || header[i] > '7') return null;
		for (int i = 136; i < 136+11; ++i)
			if (header[i] < '0' || header[i] > '7') return null;
		// enough to say it is a TAR file
		return TarDataFormat.instance;
	}
	
}
