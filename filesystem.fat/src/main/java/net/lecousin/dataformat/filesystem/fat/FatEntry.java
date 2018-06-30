package net.lecousin.dataformat.filesystem.fat;

public class FatEntry {

	protected String shortName;
	protected String longName;
	protected long cluster;
	protected long size;
	protected byte attributes;
	
	public boolean isDirectory() {
		return (attributes & 0x10) != 0;
	}
	
	public String getName() {
		if (longName != null)
			return longName;
		return shortName;
	}
	
}
