package net.lecousin.dataformat.image.png.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;

import net.lecousin.dataformat.image.test.NamedTest;
import net.lecousin.dataformat.image.test.TestImageReader;

import org.apache.sanselan.formats.png.PngImageParser;

public class SanselanImageReader implements TestImageReader, NamedTest<List<File>> {

	@Override
	public String getName() {
		return "Sanselan";
	}
	
	@Override
	public BufferedImage read(File file) {
		PngImageParser reader = new PngImageParser();
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
