package net.lecousin.dataformat.archive;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.LocalizedName;

public class ArchiveDataInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.archive",key="Contains files")
	public Long nbFiles = null;
	
}
