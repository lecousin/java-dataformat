package net.lecousin.dataformat.archive.rar;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.dataformat.archive.rar.RarArchive.Format;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.progress.WorkProgress;

abstract class RarLoader {

	static void load(RarArchive rar, WorkProgress progress, long work) {
		byte[] buf = new byte[7];
		ByteBuffer buffer = ByteBuffer.wrap(buf);
		AsyncWork<Integer,IOException> readHeader = rar.io.readFullyAsync(0, buffer);
		readHeader.listenInline(new Runnable() {
			@Override
			public void run() {
				if (readHeader.isCancelled()) {
					rar.contentLoaded.cancel(readHeader.getCancelEvent());
					return;
				}
				if (!readHeader.isSuccessful()) {
					rar.contentLoaded.error(readHeader.getError());
					return;
				}
				if (readHeader.getResult().intValue() != 7) {
					rar.contentLoaded.error(new IOException("Not a RAR archive"));
					return;
				}
				if (buf[0] != 0x52) {
					rar.contentLoaded.error(new IOException("Not a RAR archive"));
					return;
				}
				if (buf[1] == 0x45) {
					if (buf[2] == 0x7E && buf[3] == 0x5E) {
						rar.format = Format._14;
					}
				} else if (buf[1] == 0x61) {
					if (buf[2] == 0x72 && buf[3] == 0x21 && buf[4] == 0x1A && buf[5] == 0x07) {
						if (buf[6] == 0) {
							rar.format = Format._15;
						} else if (buf[6] == 1) {
							rar.format = Format._5;
						} else {
							rar.contentLoaded.error(new IOException("Unsupported RAR format (more recent than version 5)"));
							return;
						}
					}
				}
				if (rar.format == null) {
					rar.contentLoaded.error(new IOException("Not a RAR archive"));
					return;
				}
				switch (rar.format) {
				case _14:
				case _15:
					new Rar15Loader(rar, buf, buffer, progress, work).start();
					break;
				case _5:
					new Rar5Loader(rar, progress, work).start();
					break;
				}
			}
		});

	}
	
	protected RarLoader(RarArchive rar) {
		this.rar = rar;
	}
	
	protected RarArchive rar;
	
	protected abstract void start();
	
}
