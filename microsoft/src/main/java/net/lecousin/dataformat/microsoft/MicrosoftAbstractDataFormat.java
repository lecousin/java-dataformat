package net.lecousin.dataformat.microsoft;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class MicrosoftAbstractDataFormat implements DataFormat {

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/microsoft/ms_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
}
