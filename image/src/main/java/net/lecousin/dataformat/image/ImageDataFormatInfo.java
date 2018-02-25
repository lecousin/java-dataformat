package net.lecousin.dataformat.image;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.LocalizedName;

public class ImageDataFormatInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.image",key="Width")
	public long width = -1;
	@LocalizedName(namespace="dataformat.image",key="Height")
	public long height = -1;
	@LocalizedName(namespace="dataformat.image",key="Comment")
	public String comment = null;
	
}
