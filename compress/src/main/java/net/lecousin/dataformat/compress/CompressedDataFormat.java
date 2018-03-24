package net.lecousin.dataformat.compress;

import net.lecousin.dataformat.core.DataWrapperDataFormat;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class CompressedDataFormat implements DataWrapperDataFormat {

	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.compress/images/zip_", ".png", 16, 24, 32, 48, 64, 128);

	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
}
