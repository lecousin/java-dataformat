package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.FixedName;

public class ManifestDataInfo implements DataFormatInfo {

	@FixedName("Assembly Name")
	public String assembly_name;
	@FixedName("Assembly Version")
	public String assembly_version;
	
}
