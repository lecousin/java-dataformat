package net.lecousin.dataformat.archive.zip;

import java.io.File;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.application.Version;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.tasks.drives.DirectoryReader;
import net.lecousin.framework.io.util.FileInfo;
import net.lecousin.framework.util.DirectoryWalker;
import net.lecousin.framework.util.Pair;

public class TestZipCreator {
// 7-zip command line:
// "C:\Program Files\7-Zip\7z.exe" a -r -mx=9 -mmt=on test.zip src2_synchro
// with -mx=9 being maximum, we should test with different levels
// with =mmt=on enables multi-threading
	
	public static void main(String[] args) {
		Application.start(new Artifact("net.lecousin.dataformat.archive.zip", "creator.test", new Version("0")), args, true).block(0);
		System.out.println("Wait 3 seconds to make sur initialization is done");
		try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
		System.out.println("Start");
		File zipFile = new File("D:\\tmp\\test_backups\\test_zip.zip");
		if (zipFile.exists()) zipFile.delete();
		File dir = new File("D:\\tmp\\test_backups\\src2");
		//File dir = new File("D:\\tmp\\test_backups\\test");
		//File dir = new File("D:\\tmp\\test_backups\\src1");
		//File dir = new File("D:\\tmp\\test_backups\\test2");
		ZipCreator zip;
		try { zip = new ZipCreator(zipFile, Task.PRIORITY_RATHER_IMPORTANT, 256*1024*1024); }
		catch (Exception e) {
			e.printStackTrace(System.err);
			return;
		}
		long start = System.nanoTime();
		new DirectoryWalker<String>(dir, null, new DirectoryReader.Request()) {
			@Override
			protected String directoryFound(String parent, FileInfo dir, String path) {
				return path;
			}
			@Override
			protected void fileFound(String parent, FileInfo file, String path) {
				zip.add(file.file, path);
			}
		}.start(Task.PRIORITY_NORMAL, null, 0).listenInline(new Runnable() {
			@Override
			public void run() {
				System.out.println("End of walk");
				zip.noMoreFile();
			}
		});
		zip.getSynch().block(0);
		long end = System.nanoTime();
		System.out.println("End of zip in " + ((end-start)/1000000000.0d) + "s.");
		if (zip.getSynch().hasError()) {
			System.err.println("Fatal error:");
			zip.getSynch().getError().printStackTrace(System.err);
		}
		for (Pair<String,Exception> error : zip.getFilesInError()) {
			System.err.println("File error: " + error.getValue1());
			error.getValue2().printStackTrace(System.err);
		}
		try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
		LCCore.stop(true);
	}
	
}
