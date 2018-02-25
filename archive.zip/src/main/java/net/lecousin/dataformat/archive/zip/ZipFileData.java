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
import net.lecousin.framework.memory.CachedObject;

public class ZipFileData extends Data {

	ZipFileData(Data zip, ZippedFile file) {
		this.zip = zip;
		this.file = file;
	}
	
	static class InDirectory extends Data {
		InDirectory(ZipFileData data) {
			this.data = data;
		}
		ZipFileData data;
		@Override
		public String getName() {
			String name = data.getName();
			int i = name.lastIndexOf('/');
			if (i < 0) return name;
			return name.substring(i+1);
		}
		@Override
		public String getDescription() {
			return data.getDescription();
		}
		@Override
		public long getSize() {
			return data.getSize();
		}
		@Override
		public boolean hasContent() {
			return true;
		}
		@Override
		public Data getContainer() {
			return data.getContainer();
		}
		@Override
		protected AsyncWork<IO, Exception> openIO(byte priority) {
			return data.openIO(priority);
		}
	}
	
	private Data zip;
	ZippedFile file;
	
	@Override
	public String getName() {
		return file.getFilename();
	}
	
	@Override
	public String getDescription() {
		return file.getComment();
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
	protected AsyncWork<IO, Exception> openIO(byte priority) {
		AsyncWork<CachedObject<ZipArchive>,Exception> get = ZipDataFormat.cache.open(ZipFileData.this.zip, ZipFileData.this, priority, null, 0);
		AsyncWork<IO, Exception> sp = new AsyncWork<>();
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
}
