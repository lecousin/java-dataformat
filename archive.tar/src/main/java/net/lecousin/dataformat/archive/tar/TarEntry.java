package net.lecousin.dataformat.archive.tar;


public class TarEntry {

	public TarEntry(TarFile tar, long tar_pos, String path, long entry_size, boolean isDirectory) {
		this.tar = tar;
		this.tar_pos = tar_pos;
		this.path = path;
		this.entry_size = entry_size;
		this.isDirectory = isDirectory;
	}
	
	private TarFile tar;
	private long tar_pos;
	private String path;
	private long entry_size;
	private boolean isDirectory;
	
	public TarFile getTar() { return tar; }
	public long getPosition() { return tar_pos; }
	public String getPath() { return path; }
	public long getDataSize() { return entry_size; }
	public boolean isDirectory() { return isDirectory; }
	
	public String getName() {
		int i = path.lastIndexOf('/');
		if (i < 0) return path;
		return path.substring(i+1);
	}
	
}
