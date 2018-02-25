package net.lecousin.dataformat.image.bmp.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;

import net.lecousin.dataformat.image.test.NamedTest;
import net.lecousin.dataformat.image.test.TestImageReader;

import org.apache.commons.imaging.formats.bmp.BmpImageParser;

public class ApacheImageReader implements TestImageReader, NamedTest<List<File>> {

	@Override
	public String getName() {
		return "Apache commons-imaging";
	}
	
	@Override
	public BufferedImage read(File file) {
		BmpImageParser reader = new BmpImageParser();
		try {
			return reader.getBufferedImage(file, new HashMap<>());
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public void runTest(List<File> input) {
		for (File file : input)
			read(file);
	}
}
