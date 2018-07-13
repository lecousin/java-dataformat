package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.FixedName;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.uidescription.annotations.render.RendererHexa;

public class ExtFSDataFormatInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.filesystem", key="Block size")
	public long blockSize;

	@LocalizedName(namespace="dataformat.filesystem", key="Blocks per group")
	public long blocksPerGroup;

	@LocalizedName(namespace="dataformat.filesystem", key="Inodes per group")
	public long inodesPerGroup;

	@LocalizedName(namespace="dataformat.filesystem", key="Inode size")
	public int inodeSize;

	@FixedName("UUID")
	@Render(RendererHexa.class)
	public byte[] uuid;

}
