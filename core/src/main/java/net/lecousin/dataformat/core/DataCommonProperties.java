package net.lecousin.dataformat.core;

import net.lecousin.framework.geometry.HorizontalAlignment;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererSize;
import net.lecousin.framework.uidescription.annotations.render.TextAlign;

public class DataCommonProperties {

	@LocalizedName(namespace="b",key="Size")
	@Render(RendererSize.class)
	@TextAlign(HorizontalAlignment.RIGHT)
	public Long size;
	
	public static class Format extends DataCommonProperties {
		@LocalizedName(namespace="dataformat",key="File Type")
		public ILocalizableString type;
	}
	
}
