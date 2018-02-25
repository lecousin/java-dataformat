package net.lecousin.dataformat.text;

import java.nio.charset.StandardCharsets;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class TextDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return null;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { TextDataFormat.instance };
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xFE) {
			// looks like an UTF-16 BOM
			if (isValidUTF16(header, headerLength, 2, true)) {
				data.setProperty(TextDataFormat.PROPERTY_CHARSET, StandardCharsets.UTF_16LE);
				return TextDataFormat.instance;
			}
		} else if ((header[0] & 0xFF) == 0xFE && (header[1] & 0xFF) == 0xFF) {
			// looks like an UTF-16 BOM
			if (isValidUTF16(header, headerLength, 2, false)) {
				data.setProperty(TextDataFormat.PROPERTY_CHARSET, StandardCharsets.UTF_16BE);
				return TextDataFormat.instance;
			}
		}
		boolean usAscii = true;
		boolean extAscii = true;
		boolean utf8 = true;
		int utf8_pos = 0;
		int utf8_bytes = 0;
		for (int i = 0; i < headerLength; ++i) {
			int c = header[i]&0xFF;
			if (c >= 0x80) usAscii = false;
			else if (c < 32 && c != '\t' && c != '\n' && c != '\r') extAscii = usAscii = false;
			if (utf8) {
				switch (utf8_pos) {
				case 0:
					if ((c & 0x80) == 0) {
						if (c < 32 && c != '\t' && c != '\n' && c != '\r') utf8 = false;
						break;
					}
					if ((c & 0x40) == 0) { utf8 = false; break; }
					utf8_pos = 1;
					if ((c & 0x20) == 0) { utf8_bytes = 2; break; }
					if ((c & 0x10) == 0) { utf8_bytes = 3; break; }
					if ((c & 0x8) == 0) { utf8_bytes = 4; break; }
					utf8 = false;
					break;
				default:
					if ((c & 0xC0) != 0x80) { utf8 = false; break; }
					if (++utf8_pos == utf8_bytes) {
						utf8_pos = 0;
						utf8_bytes = 0;
					}
				}
			}
			if (!usAscii && !extAscii && !utf8)
				return null;
		}
		// TODO
		if (usAscii)
			data.setProperty(TextDataFormat.PROPERTY_CHARSET, StandardCharsets.US_ASCII);
		else if (utf8)
			data.setProperty(TextDataFormat.PROPERTY_CHARSET, StandardCharsets.UTF_8);
		return TextDataFormat.instance;
	}
	
	private static boolean isValidUTF16(byte[] data, int len, int pos, boolean littleEndian) {
		int nb = (len - pos) / 2;
		for (int i = 0; i < nb; ++i) {
			int c = littleEndian ? DataUtil.readUnsignedShortLittleEndian(data, pos + i*2) : DataUtil.readUnsignedShortBigEndian(data, pos + i*2);
			if (c >= 0xDC00 && c <= 0xDFFF)
				return false;
			if (c >= 0xD800 && c <= 0xDBFF) {
				// high surrogate, next 16-bit value must be a valid low surrogate
				if (i == nb - 1)
					return false;
				i++;
				c = littleEndian ? DataUtil.readUnsignedShortLittleEndian(data, pos + i*2) : DataUtil.readUnsignedShortBigEndian(data, pos + i*2);
				if (c <= 0xDC00 || c >= 0xDFFF)
					return false;
			}
		}
		return true;
	}
	
}
