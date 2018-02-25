package net.lecousin.dataformat.image.png.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

import net.lecousin.dataformat.image.test.NamedTest;
import net.lecousin.dataformat.image.test.TestImageReader;

public class ImageIOImageReader implements TestImageReader, NamedTest<List<File>> {

	public ImageIOImageReader(ImageReader reader) {
		this.reader = reader;
	}
	
	private ImageReader reader;
	
	@Override
	public String getName() {
		return "ImageIO: "+reader.getClass().getName();
	}
	
	@Override
	public BufferedImage read(File file) {
		try {
			reader.setInput(new FileImageInputStream(file));
			return reader.read(0, null);
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
