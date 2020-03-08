package net.lecousin.dataformat.compress.gzip;

import net.lecousin.dataformat.compress.CompressedDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
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
		protected AsyncSupplier<GZipHeader, Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			AsyncSupplier<GZipHeader, Exception> result = new AsyncSupplier<>();
			GZipHeader h = new GZipHeader();
			h.read(io).onDone(() -> {
				result.unblockSuccess(h);
			}, result, e -> e);
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
	public AsyncSupplier<Data, Exception> getWrappedData(Data data, WorkProgress progress, long work) {
		AsyncSupplier<Data, Exception> result = new AsyncSupplier<>();
		cache.open(data, this, Priority.NORMAL, progress, work).onDone((res) -> {
			result.unblockSuccess(new GZippedData(data, res.get()));
			res.release(GZipDataFormat.this);
		}, result);
		return result;
	}
	
	@Override
	public AsyncSupplier<GZipDataFormatInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<GZipDataFormatInfo, Exception> result = new AsyncSupplier<>();
		cache.open(data, this, Priority.NORMAL, null, 0).onDone((res) -> {
			GZipDataFormatInfo info = new GZipDataFormatInfo();
			info.comment = res.get().comment;
			result.unblockSuccess(info);
			res.release(GZipDataFormat.this);
		}, result);
		return result;
	}

}
