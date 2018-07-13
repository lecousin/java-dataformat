package net.lecousin.dataformat.filesystem.fat;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererHexa;

public class FATDataFormatInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.filesystem", key="Bytes per sector")
	public int bytesPerSector;
	
	@LocalizedName(namespace="dataformat.filesystem", key="Sectors per cluster")
	public short sectorsPerCluster;
	
	@LocalizedName(namespace="dataformat.filesystem", key="Volume label")
	public String volumeLabel;
	
	@LocalizedName(namespace="dataformat.filesystem", key="Serial number")
	@Render(RendererHexa.class)
	public long serialNumber;
	
	@LocalizedName(namespace="dataformat.filesystem", key="formatter")
	public String formatterSystem;
	
}
