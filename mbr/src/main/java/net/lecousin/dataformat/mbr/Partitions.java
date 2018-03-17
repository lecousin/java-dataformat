package net.lecousin.dataformat.mbr;

import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.system.hardware.DiskPartition;

public class Partitions {

	public static class Partition {
		public DiskPartition partitionInfo;
		public Data data;
	}
	
	public List<Partition> list = new ArrayList<>();
	
}
