package net.lecousin.dataformat.image.bmp.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import net.lecousin.dataformat.image.bmp.io.BMPReader;
import net.lecousin.dataformat.image.test.NamedTest;
import net.lecousin.dataformat.image.test.TestImageReader;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.FileIO;

public class MyImageReader implements TestImageReader, NamedTest<List<File>> {

	@Override
	public String getName() {
		return "net.lecousin.dataformat";
	}
	
	@Override
	public BufferedImage read(File file) {
		try (FileIO.ReadOnly io = new FileIO.ReadOnly(file, Task.Priority.NORMAL)) {
			AsyncSupplier<BufferedImage,Exception> read = BMPReader.read(io);
			read.blockThrow(0);
			return read.getResult();
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
