package net.lecousin.dataformat.image.png;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class PNGDataFormat extends ImageDataFormat {

	public static PNGDataFormat instance = new PNGDataFormat();
	
	public static Log log = LogFactory.getLog("PNG");
	
	private PNGDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("PNG");
	}
	
	public static final String[] extensions = new String[] { "png" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] { "image/png" };
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
	@Override
	public AsyncWork<DataFormatInfo,Exception> getInfo(final Data data, final byte priority) {
		AsyncWork<DataFormatInfo,Exception> sp = new AsyncWork<>();
		AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.open(priority);
		ByteBuffer buf = ByteBuffer.allocate(4+4+13);
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				if (open.isCancelled()) return;
				if (!open.isSuccessful()) {
					sp.unblockError(open.getError());
					return;
				}
				@SuppressWarnings("resource")
				IO.Readable.Seekable io = open.getResult();
				AsyncWork<Integer, IOException> read = io.readFullyAsync(8,buf);
				sp.onCancel(new Listener<CancelException>() {
					@Override
					public void fire(CancelException event) {
						read.unblockCancel(event);
						if (read.isCancelled())
							io.closeAsync();
					}
				});
				read.listenInline(new Runnable() {
					@Override
					public void run() {
						if (read.isCancelled()) return;
						if (!read.isSuccessful()) {
							io.closeAsync();
							sp.unblockError(read.getError());
							return;
						}
						Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Reading PNG metadata", priority) {
							@Override
							public Void run() {
								try {
									buf.flip();
									int len = buf.getInt();
									if (len < 8) {
										sp.unblockSuccess(null);
										return null;
									}
									int type = buf.getInt();
									if (type != (('I'<<24) | ('H'<<16) | ('D'<<8) | ('R'))) {
										sp.unblockSuccess(null);
										return null;
									}
									PNGDataFormatInfo info = new PNGDataFormatInfo();
									info.width = buf.getInt();
									info.height = buf.getInt();
									// continue: http://www.libpng.org/pub/png/spec/1.2/PNG-Chunks.html#C.IHDR
									sp.unblockSuccess(info);
									return null;
								} catch (Exception e) {
									sp.unblockError(e);
									LCCore.getApplication().getDefaultLogger().error("Error reading PNG", e);
									return null;
								} finally {
									io.closeAsync();
								}
							}
						};
						task.start();
					}
				});
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				open.unblockCancel(event);
			}
		});
		return sp;
	}
	
}
