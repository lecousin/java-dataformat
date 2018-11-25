package net.lecousin.dataformat.image.png.test;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import net.lecousin.dataformat.image.test.NamedTest;
import net.lecousin.dataformat.image.test.PerformanceComparison;
import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.application.Version;

public class ComparePerf {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		try {
			Application.start(new Artifact("net.lecousin.dataformat.image.png", "compare-perf.test", new Version("0")), args, false).blockThrow(0);
			URL url = TestDisplayPNGSuite.class.getClassLoader().getResource("net/lecousin/dataformat/image/png/test/pngsuite/basi0g01.png");
			File file = new File(url.toURI());
			File dir = file.getParentFile();
			File[] files = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".png") && !pathname.getName().toLowerCase().startsWith("x");
				}
			});
			ArrayList<File> inputs = new ArrayList<File>();
			Collections.addAll(inputs, files);

			System.out.println("Pause 5 seconds");
			try { Thread.sleep(5000); }
			catch (InterruptedException e) {}
			/*
			inputs.clear();
			inputs.add(files[0]);
			*/
			/*
			new MyImageReader().read(files[0]);
			Threading.traceTaskTime = true;
			new MyImageReader().read(files[0]);
			*/
			
			/*
			System.out.println("File: "+inputs.get(0).getName());
			try { Thread.sleep(30000); } catch (InterruptedException e) {}
			for (int i = 0; i < 200; ++i)
				new MyImageReader().read(files[0]);
			System.exit(0);
			*/
			
			ArrayList<NamedTest<List<File>>> readers = new ArrayList<>();
			readers.add(new MyImageReader());
			readers.add(new SanselanImageReader());
			readers.add(new ApacheImageReader());
			for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("png"); it.hasNext(); )
				readers.add(new ImageIOImageReader(it.next()));
			System.out.println(" ***** Test small files *****");
			PerformanceComparison.compare(200, inputs, readers.toArray(new NamedTest[readers.size()]));

			System.out.println(" ***** Test small files again *****");
			PerformanceComparison.compare(200, inputs, readers.toArray(new NamedTest[readers.size()]));

			System.out.println(" ***** Test medium files (256x256) *****");
			dir = new File(dir.getParentFile(), "images");
			inputs.clear();
			inputs.add(new File(dir, "GreyAlpha_8bits_256x256_interlace.png"));
			inputs.add(new File(dir, "GreyAlpha_8bits_256x256.png"));
			inputs.add(new File(dir, "Indexed_8bits_256x256_interlace.png"));
			inputs.add(new File(dir, "Indexed_8bits_256x256.png"));
			inputs.add(new File(dir, "RGBA_8bits_256x256_interlace.png"));
			inputs.add(new File(dir, "RGBA_8bits_256x256.png"));
			PerformanceComparison.compare(200, inputs, readers.toArray(new NamedTest[readers.size()]));
			System.out.println(" ***** Test big files *****");
			dir = new File(dir.getParentFile(), "big_images");
			inputs.clear();
			inputs.add(new File(dir, "100_0032.png"));
			inputs.add(new File(dir, "100_0125.png"));
			PerformanceComparison.compare(20, inputs, readers.toArray(new NamedTest[readers.size()]));
			
			LCCore.stop(true);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}
	
}
