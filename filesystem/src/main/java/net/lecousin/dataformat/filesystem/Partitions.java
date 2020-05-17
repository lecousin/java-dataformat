package net.lecousin.dataformat.filesystem;

import java.util.ArrayList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.system.hardware.drive.DiskPartition;

public class Partitions {

	public static class Partition {
		public DiskPartition partitionInfo;
		public Data data;
	}
	
	public List<Partition> list = new ArrayList<>();
	
}
