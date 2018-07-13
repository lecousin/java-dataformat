package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererTimestamp;

public class ExtFSEntryProperties extends DataCommonProperties {

	@LocalizedName(namespace="dataformat.filesystem", key="Last access")
	@Render(RendererTimestamp.class)
	public long lastAccessTime;

	@LocalizedName(namespace="dataformat.filesystem", key="Last modification")
	@Render(RendererTimestamp.class)
	public long lastModificationTime;

	@LocalizedName(namespace="dataformat.filesystem", key="Hard links")
	public int hardLinks;
	
	@LocalizedName(namespace="dataformat.filesystem", key="Owner id")
	public int uid;
	
	@LocalizedName(namespace="dataformat.filesystem", key="Owner group id")
	public int gid;
}
