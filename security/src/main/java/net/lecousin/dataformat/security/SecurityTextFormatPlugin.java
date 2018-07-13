package net.lecousin.dataformat.security;

import java.util.ArrayList;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.text.TextSpecializationDetectorWithFirstLines;
import net.lecousin.framework.util.UnprotectedString;

public class SecurityTextFormatPlugin implements TextSpecializationDetectorWithFirstLines.Plugin {
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			Base64EncodedCertificateDataFormat.instance
		};
	}
	
	@Override
	public DataFormat detect(Data data, ArrayList<UnprotectedString> lines, char[] allHeaderChars, int nbHeaderChars) {
		UnprotectedString line = lines.get(0);
		if (line.length() >= 15 &&
			line.charAt(0) == '-' &&
			line.charAt(1) == '-' &&
			line.charAt(2) == '-' &&
			line.charAt(3) == '-' &&
			line.charAt(4) == '-' &&
			line.charAt(5) == 'B' &&
			line.charAt(6) == 'E' &&
			line.charAt(7) == 'G' &&
			line.charAt(8) == 'I' &&
			line.charAt(9) == 'N' 
		) {
			int i = line.length();
			if (line.charAt(i-1) == '-' &&
				line.charAt(i-2) == '-' &&
				line.charAt(i-3) == '-' &&
				line.charAt(i-4) == '-' &&
				line.charAt(i-5) == '-'
			) {
				// looks like a PEM certificate
				// TODO should we check that lines before -----END are 64 characters ?
				return Base64EncodedCertificateDataFormat.instance;
			}
		}
		return null;
	}

}
