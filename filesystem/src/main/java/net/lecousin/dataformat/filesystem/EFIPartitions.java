package net.lecousin.dataformat.filesystem;

import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.GUIDRenderer;
import net.lecousin.framework.uidescription.annotations.render.Render;

public class EFIPartitions {

	public static class Partition {
		public GPT gpt;
		public int partitionIndex;
		public Data data;
	}
	
	public List<Partition> list = new ArrayList<>();
	
	public static class PartitionCommonProperties extends DataCommonProperties {
		
		@LocalizedName(namespace="dataformat.filesystem", key="Partition type")
		@Render(GUIDRenderer.class)
		public byte[] partitionType;
		
		@LocalizedName(namespace="dataformat.filesystem", key="Partition identifier")
		@Render(GUIDRenderer.class)
		public byte[] partitionGUID;
		
	}
	
}
