package net.lecousin.dataformat.image.ico;

import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.image.ImageDataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class ICOImageInfo {

	public static class DataInfo extends ImageDataFormatInfo {
		
	}
	
	public static class CommonProperties extends DataCommonProperties {
		
		@LocalizedName(namespace="dataformat.image",key="Width")
		public long width = -1;
		@LocalizedName(namespace="dataformat.image",key="Height")
		public long height = -1;
		
	}
	
}
