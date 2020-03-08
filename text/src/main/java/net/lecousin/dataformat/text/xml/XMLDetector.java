package net.lecousin.dataformat.text.xml;

import java.util.ArrayList;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.text.TextSpecializationDetectorWithFirstLines;
import net.lecousin.framework.text.CharArrayString;
import net.lecousin.framework.xml.XMLStreamReader;

public class XMLDetector implements TextSpecializationDetectorWithFirstLines.Plugin {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			XMLDataFormat.instance
		};
	}
	
	@Override
	public DataFormat detect(Data data, ArrayList<CharArrayString> lines, char[] allHeaderChars, int nbHeaderChars) {
		// if the first lines starts with a <?xml tag, we are sure
		CharArrayString line = lines.get(0);
		if (line.startsWith("<?") && line.length() >= 5) {
			char c1 = line.charAt(2);
			char c2 = line.charAt(3);
			char c3 = line.charAt(4);
			if ((c1 == 'x' || c1 == 'X') &&
				(c2 == 'm' || c2 == 'M') &&
				(c3 == 'l' || c3 == 'L'))
				return XMLDataFormat.instance;
		}
		// the first non-blank character must be a <
		for (int i = 0; i < nbHeaderChars; ++i) {
			if (isBlank(allHeaderChars[i])) continue;
			if (allHeaderChars[i] != '<')
				return null;
			if (i < nbHeaderChars) {
				char c = allHeaderChars[i + 1];
				if (c != '!' && c != '?' && !XMLStreamReader.isNameStartChar(c))
					return null;
				// TODO continue to analyse to improve detection
			}
			return XMLDataFormat.instance;
		}
		return null;
	}
	
	public static boolean isBlank(char c) {
		return c == ' ' || c == '\t' || c == '\r' || c == '\n';
	}
}
