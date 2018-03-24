package net.lecousin.dataformat.image;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class ImageDataFormat implements DataFormat {

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/image/picture_", ".png", 16, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static abstract class Multiple extends ImageDataFormat implements ContainerDataFormat {
		
	}
	
}
