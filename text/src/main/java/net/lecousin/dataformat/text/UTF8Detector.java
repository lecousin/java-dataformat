package net.lecousin.dataformat.text;

import java.nio.charset.StandardCharsets;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public class UTF8Detector implements DataFormatDetector.OnlyHeaderNeeded {
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF })
		};
	}
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { TextDataFormat.instance };
	}
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		for (int i = 3; i < headerLength; ++i) {
			if ((header[i] & 0x80) == 0) {
				continue; // 1 byte
			}
			if ((header[i] & 0x40) == 0)
				return null; // invalid
			if (i == headerLength-1)
				break;
			if ((header[i+1] & 0xC0) != 0x80)
				return null; // invalid
			if ((header[i] & 0x20) == 0) {
				// 2 bytes
				i++;
				continue;
			}
			if (i == headerLength-2)
				break;
			if ((header[i+2] & 0xC0) != 0x80)
				return null; // invalid
			if ((header[i] & 0x10) == 0) {
				// 3 bytes
				i += 2;
				continue;
			}
			if ((header[i] & 0x8) != 0)
				return null; // invalid
			if (i == headerLength-3)
				break;
			if ((header[i+3] & 0xC0) != 0x80)
				return null; // invalid
			// 4 bytes
			i += 3;
		}
		data.setProperty(TextDataFormat.PROPERTY_CHARSET, StandardCharsets.UTF_8);
		return TextDataFormat.instance;
	}
}
