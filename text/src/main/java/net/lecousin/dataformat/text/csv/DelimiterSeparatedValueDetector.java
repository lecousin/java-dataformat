package net.lecousin.dataformat.text.csv;

import java.util.ArrayList;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.text.TextSpecializationDetectorWithFirstLines;
import net.lecousin.framework.text.CharArrayString;

public class DelimiterSeparatedValueDetector implements TextSpecializationDetectorWithFirstLines.Plugin {

	public static final String DSV_DELIMITER_PROPERTY = "dataformat.dsv.delimiter";
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			DelimiterSeaparatedValuesFormat.instance
		};
	}
	
	@Override
	public DataFormat detect(Data data, ArrayList<CharArrayString> lines, char[] allHeaderChars, int nbHeaderChars) {
		if (lines.size() < 2)
			return null; // if only one line, we cannot check there is the same number of columns, so we cannot tell the format
		// try to detect most common: comma, tab, colon, pipe
		int[] counts1 = countCharacters(lines.get(0));
		if (counts1[0] == 0 && counts1[1] == 0 && counts1[2] == 0 && counts1[3] == 0)
			return null;
		int[] counts2 = countCharacters(lines.get(1));
		if (counts1[1] == counts2[1] && counts1[1] > 0) {
			// both first lines contain same number of tab => check all lines
			boolean ok = true;
			for (int i = 2; ok && i < lines.size(); ++i)
				ok &= lines.get(i).countChar('\t') == counts1[1];
			if (ok) {
				data.setProperty(DSV_DELIMITER_PROPERTY, new Character('\t'));
				return DelimiterSeaparatedValuesFormat.instance;
			}
		}
		if (counts1[2] == counts2[2] && counts1[2] > 0) {
			// both first lines contain same number of colon => check all lines
			boolean ok = true;
			for (int i = 2; ok && i < lines.size(); ++i)
				ok &= lines.get(i).countChar(':') == counts1[2];
			if (ok) {
				data.setProperty(DSV_DELIMITER_PROPERTY, new Character(':'));
				return DelimiterSeaparatedValuesFormat.instance;
			}
		}
		if (counts1[3] == counts2[3] && counts1[3] > 0) {
			// both first lines contain same number of pipe => check all lines
			boolean ok = true;
			for (int i = 2; ok && i < lines.size(); ++i)
				ok &= lines.get(i).countChar('|') == counts1[3];
			if (ok) {
				data.setProperty(DSV_DELIMITER_PROPERTY, new Character('|'));
				return DelimiterSeaparatedValuesFormat.instance;
			}
		}
		if (counts1[0] > 0 && counts2[0] > 0) {
			// both first lines contain comma
			// with comma as separator (CSV) double quotes can be used
			int fields = -1;
			int currentFields = 1;
			boolean inQuotes = false;
			int nbLines = 0;
			for (CharArrayString s : lines) {
				char[] chars = s.charArray();
				int off = s.arrayStart();
				int len = s.length();
				for (int i = off; i < off+len; ++i) {
					if (chars[i] == '"') {
						if (!inQuotes) {
							inQuotes = true;
							continue;
						}
						if (i < off+len-1 && chars[i+1] == '"') {
							i++;
							continue;
						}
						inQuotes = false;
						continue;
					}
					if (inQuotes) continue;
					if (chars[i] == ',')
						currentFields++;
				}
				// end of line
				if (inQuotes) continue;
				if (fields < 0) fields = currentFields;
				else if (fields != currentFields) return null;
				nbLines++;
				currentFields = 1;
			}
			if (nbLines > 1) {
				data.setProperty(DSV_DELIMITER_PROPERTY, new Character(','));
				return DelimiterSeaparatedValuesFormat.instance;
			}
		}
		return null;
	}
	
	private static int[] countCharacters(CharArrayString line) {
		int[] counts = new int[4];
		char[] chars = line.charArray();
		int offset = line.arrayStart();
		for (int i = 0; i < line.length(); ++i)
			switch (chars[offset+i]) {
			case ',': counts[0]++; break;
			case '\t': counts[1]++; break;
			case ':': counts[2]++; break;
			case '|':counts[3]++; break;
			}
		return counts;
	}
	
}
