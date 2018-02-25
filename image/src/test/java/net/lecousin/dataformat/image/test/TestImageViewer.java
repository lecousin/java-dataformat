package net.lecousin.dataformat.image.test;

import java.awt.image.BufferedImage;
import java.io.File;

import net.lecousin.dataformat.image.test.ImageViewer.ImagesProvider;
import net.lecousin.framework.mutable.MutableInteger;

public class TestImageViewer {

	public static void test(File[] files, TestImageReader[] readers) {
		String[] titles = new String[readers.length];
		for (int i = 0; i < readers.length; ++i)
			titles[i] = readers[i].getName();
		MutableInteger fileIndex = new MutableInteger(0);
		ImageViewer viewer = new ImageViewer(titles, new ImagesProvider() {
			@Override
			public BufferedImage[] nextImages(StringBuffer name) {
				if (fileIndex.get() >= files.length)
					return null;
				File file = files[fileIndex.get()];
				fileIndex.inc();
				name.append(file.getName());
				BufferedImage[] images = new BufferedImage[readers.length];
				for (int i = 0; i < readers.length; ++i) {
					System.out.println("Reading "+file.getName()+" with "+readers[i].getName());
					try {
						images[i] = readers[i].read(file);
					} catch (Throwable t) {
						System.err.println("Error in reader " + readers[i].getName());
						t.printStackTrace(System.err);
					}
				}
				return images;
			}
		});
		viewer.setVisible(true);
	}
	
}
