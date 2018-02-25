package net.lecousin.dataformat.image.test;

import java.awt.image.BufferedImage;
import java.io.File;

public interface TestImageReader {

	public String getName();
	
	public BufferedImage read(File file);
	
}
