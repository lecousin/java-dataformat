package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.vm.vbox.VirtualBoxDiskImage.ImageType;
import net.lecousin.framework.uidescription.annotations.name.FixedName;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererSize;

public class VDIDataFormatInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.vm.vbox",key="Version")
	public String version;

	@LocalizedName(namespace="dataformat.vm.vbox",key="Virtual Disk Image Type")
	public ImageType imageType;

	@LocalizedName(namespace="dataformat.vm.vbox",key="Comment")
	public String comment;
	
	@LocalizedName(namespace="dataformat.vm.vbox",key="Disk size")
	@Render(RendererSize.class)
	public long size;
	
	@LocalizedName(namespace="dataformat.vm.vbox",key="Block size")
	@Render(RendererSize.class)
	public long blockSize;
	
	@LocalizedName(namespace="dataformat.vm.vbox",key="Blocks number")
	public long nbBlocks;
	
	@LocalizedName(namespace="dataformat.vm.vbox",key="Allocated blocks number")
	public long nbBlocksAllocated;
	
	@FixedName("UUID")
	public String uid;
	
}
