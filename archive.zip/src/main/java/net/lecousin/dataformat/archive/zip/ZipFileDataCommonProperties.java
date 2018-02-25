package net.lecousin.dataformat.archive.zip;

import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererTimestamp;

public class ZipFileDataCommonProperties extends DataCommonProperties {

	@LocalizedName(namespace="b",key="Last Modified")
	@Render(RendererTimestamp.class)
	public Long lastModificationTimestamp;
	
	@LocalizedName(namespace="b",key="Last Access")
	@Render(RendererTimestamp.class)
	public Long lastAccessTimestamp;
	
	@LocalizedName(namespace="b",key="Created")
	@Render(RendererTimestamp.class)
	public Long creationTimestamp;

}
