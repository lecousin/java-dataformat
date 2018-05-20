package net.lecousin.dataformat.archive.coff;

import net.lecousin.dataformat.archive.coff.COFFArchive.COFFFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.framework.locale.FixedLocalizedString;

public class COFFArchiveSubData extends SubData {

	public COFFArchiveSubData(Data parent, COFFFile file) {
		super(parent, file.offset, file.size, new FixedLocalizedString(file.name));
		coffFile = file;
	}
	
	public COFFFile coffFile;
	
}
