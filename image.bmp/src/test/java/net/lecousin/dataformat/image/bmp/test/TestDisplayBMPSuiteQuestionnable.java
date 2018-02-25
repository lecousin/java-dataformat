package net.lecousin.dataformat.image.bmp.test;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import net.lecousin.dataformat.image.test.TestImageReader;
import net.lecousin.dataformat.image.test.TestImageViewer;
import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.Version;

public class TestDisplayBMPSuiteQuestionnable {

	public static void main(String[] args) {
		try {
			Application.start(new Artifact("net.lecousin.dataformat.image.bmp", "bmp-suite.test", new Version("0")), args, true).blockThrow(0);
			URL url = TestDisplayBMPSuiteQuestionnable.class.getClassLoader().getResource("net/lecousin/dataformat/image/bmp/test/bmpsuite-2.5/q/pal1huff.bmp");
			File file = new File(url.toURI());
			File dir = file.getParentFile();
			File[] files = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isFile();
				}
			});
			ArrayList<TestImageReader> readers = new ArrayList<>();
			readers.add(new MyImageReader());
			//readers.add(new SanselanImageReader());
			//readers.add(new ApacheImageReader());
			for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("bmp"); it.hasNext(); )
				readers.add(new ImageIOImageReader(it.next()));
			TestImageViewer.test(files, readers.toArray(new TestImageReader[readers.size()]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
