package net.lecousin.dataformat.archive;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class ArchiveDataFormat implements ContainerDataFormat {

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/archive/archive_", ".png", 16, 32, 48, 64, 128);
	
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
}
