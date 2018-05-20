package net.lecousin.dataformat.security;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class X509CertificateDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 0x30 })
		};
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { X509CertificateDataFormat.instance };
	}

	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		// ASN.1 format, starting with a structure and its size should be the size of the data
		if (headerLength < 2)
			return null;
		int i = header[1] & 0xFF;
		int start;
		// start with a definite size
		if ((i & 0x80) == 0) {
			// short size
			if (i != dataSize - 2)
				return null;
			start = 2;
		} else {
			// long size
			i = i & 0x7F;
			if (i > 4)
				return null;
			if (headerLength < 2 + i)
				return null;
			start = 2 + i;
			if (i == 1) {
				if ((header[2] & 0xFF) != dataSize - 3)
					return null;
			} else if (i == 2) {
				i = DataUtil.readUnsignedShortBigEndian(header, 2);
				if (i != dataSize - 4)
					return null;
			} else if (i == 3) {
				i = ((header[2] & 0xFF) << 16) | ((header[3] & 0xFF) << 8) | (header[4] & 0xFF);
				if (i != dataSize - 5)
					return null;
			} else {
				long l = DataUtil.readUnsignedIntegerBigEndian(header, 2);
				if (l != dataSize - 6)
					return null;
			}
		}
		// next is again a structure
		if (headerLength < start)
			return null;
		if (header[start] != 0x30)
			return null;
		return X509CertificateDataFormat.instance;
	}

}
