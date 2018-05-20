package net.lecousin.dataformat.archive.zip;

import java.io.IOException;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.event.Listener;
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
	protected AsyncWork<IO.Readable, Exception> openIOReadOnly(byte priority) {
		AsyncWork<CachedObject<ZipArchive>,Exception> get = ZipDataFormat.cache.open(ZipFileData.this.zip, ZipFileData.this, priority, null, 0);
		AsyncWork<IO.Readable, Exception> sp = new AsyncWork<>();
		get.listenInline(new AsyncWorkListener<CachedObject<ZipArchive>, Exception>() {
			@Override
			public void ready(CachedObject<ZipArchive> zip) {
				AsyncWork<IO.Readable,IOException> uncompress = file.uncompress(zip.get(), priority);
				uncompress.listenInline(new AsyncWorkListener<IO.Readable, IOException>() {
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
				sp.unblockError(error);
			}
			@Override
			public void cancelled(CancelException event) {
				sp.unblockCancel(event);
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				get.unblockCancel(event);
			}
		});
		return sp;
	}
	
	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	
	@Override
	protected <T extends IO.Readable.Seekable & IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		return null;
	}
}
