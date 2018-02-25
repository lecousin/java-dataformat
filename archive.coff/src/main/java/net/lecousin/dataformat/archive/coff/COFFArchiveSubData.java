package net.lecousin.dataformat.archive.coff;

import net.lecousin.dataformat.archive.coff.COFFArchive.COFFFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.SubData;

public class COFFArchiveSubData extends SubData {

	public COFFArchiveSubData(Data parent, COFFFile file) {
		super(parent, file.offset, file.size, file.name);
		coffFile = file;
	}
	
	public COFFFile coffFile;
	
}
