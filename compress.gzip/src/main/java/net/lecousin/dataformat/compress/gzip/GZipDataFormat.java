package net.lecousin.dataformat.compress.gzip;

import net.lecousin.dataformat.compress.CompressedDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;

public class GZipDataFormat extends CompressedDataFormat {

	public static final GZipDataFormat instance = new GZipDataFormat();
	
	private GZipDataFormat() { /* singleton. */ }

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("GZip Compressed");
	}
	
	public static String[] extensions = new String[] { "gz", "tgz", "gzip" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}

	public static String[] mimes = new String[] { 
		"application/x-gzip",
		"application/gzip", 
		"application/x-gunzip", 
		"application/gzipped", 
		"application/gzip-compressed", 
		"gzip/document"
	};
	
	@Override
	public String[] getMIMETypes() {
		return mimes;
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		return p;
	}

	public static OpenedDataCache<GZipHeader> cache = new OpenedDataCache<GZipHeader>(GZipHeader.class, 30*1000) {

		@Override
		protected AsyncWork<GZipHeader, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncWork<GZipHeader, Exception> result = new AsyncWork<>();
			GZipHeader h = new GZipHeader();
			h.read(io).listenInlineSP(() -> {
				result.unblockSuccess(h);
			}, result);
			if (progress != null) progress.progress(work);
			return result;
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(GZipHeader header) {
		}
		
	};
	
	@Override
	public AsyncWork<Data, Exception> getWrappedData(Data data, WorkProgress progress, long work) {
		AsyncWork<Data, Exception> result = new AsyncWork<>();
		cache.open(data, this, Task.PRIORITY_NORMAL, progress, work).listenInline((res) -> {
			result.unblockSuccess(new GZippedData(data, res.get()));
			res.release(GZipDataFormat.this);
		}, result);
		return result;
	}
	
	@Override
	public AsyncWork<GZipDataFormatInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<GZipDataFormatInfo, Exception> result = new AsyncWork<>();
		cache.open(data, this, Task.PRIORITY_NORMAL, null, 0).listenInline((res) -> {
			GZipDataFormatInfo info = new GZipDataFormatInfo();
			info.comment = res.get().comment;
			result.unblockSuccess(info);
			res.release(GZipDataFormat.this);
		}, result);
		return result;
	}

}
