package net.lecousin.dataformat.archive.zip;

import java.io.IOException;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.AsyncSupplier.Listener;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableStringBuffer;
import net.lecousin.framework.memory.CachedObject;

public class ZipFileData extends Data {

	ZipFileData(Data zip, ZippedFile file) {
		this.zip = zip;
		this.file = file;
	}
	
	private Data zip;
	ZippedFile file;
	
	@Override
	public FixedLocalizedString getName() {
		String n = file.getFilename();
		int i = n.lastIndexOf('/');
		if (i < 0) return new FixedLocalizedString(n);
		return new FixedLocalizedString(n.substring(i + 1));
	}
	
	@Override
	public ILocalizableString getDescription() {
		return new LocalizableStringBuffer(zip.getDescription(), "/", file.getFilename());
	}
	
	@Override
	public long getSize() {
		return file.getUncompressedSize();
	}
	
	@Override
	public boolean hasContent() {
		return true;
	}
	@Override
	public Data getContainer() {
		return zip;
	}
	
	@Override
	protected AsyncSupplier<IO.Readable, IOException> openIOReadOnly(Priority priority) {
		AsyncSupplier<CachedObject<ZipArchive>,Exception> get = ZipDataFormat.cache.open(ZipFileData.this.zip, ZipFileData.this, priority, null, 0);
		AsyncSupplier<IO.Readable, IOException> sp = new AsyncSupplier<>();
		get.listen(new Listener<CachedObject<ZipArchive>, Exception>() {
			@Override
			public void ready(CachedObject<ZipArchive> zip) {
				AsyncSupplier<IO.Readable,IOException> uncompress = file.uncompress(zip.get(), priority);
				uncompress.listen(new Listener<IO.Readable, IOException>() {
					@Override
					public void ready(Readable result) {
						result.addCloseListener(new Runnable() {
							@Override
							public void run() {
								zip.release(ZipFileData.this);
							}
						});
						sp.unblockSuccess(result);
					}
					@Override
					public void error(IOException error) {
						zip.release(ZipFileData.this);
						sp.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						zip.release(ZipFileData.this);
						sp.unblockCancel(event);
					}
				});
			}
			@Override
			public void error(Exception error) {
				sp.unblockError(IO.error(error));
			}
			@Override
			public void cancelled(CancelException event) {
				sp.unblockCancel(event);
			}
		});
		sp.onCancel(get::cancel);
		return sp;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncSupplier<T, IOException> openIOReadWrite(Priority priority) {
		return null;
	}
}
