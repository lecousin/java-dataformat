package net.lecousin.dataformat.document.openoffice;

import java.io.IOException;
import java.nio.charset.Charset;

import net.lecousin.dataformat.archive.zip.ZipArchive;
import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.text.CharArrayStringBuffer;

public class OpenOfficeDetector implements DataFormatSpecializationDetector {

	@Override
	public DataFormat getBaseFormat() {
		return ZipDataFormat.instance;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			OpenDocumentChartDataFormat.instance,
			OpenDocumentFormulaDataFormat.instance,
			OpenDocumentGraphicsDataFormat.instance,
			OpenDocumentImageDataFormat.instance,
			OpenDocumentPresentationDataFormat.instance,
			OpenDocumentSpreadsheetDataFormat.instance,
			OpenDocumentTextDataFormat.instance,
		};
	}
	
	@Override
	public AsyncSupplier<DataFormat,NoException> detectSpecialization(Data data, Priority priority, byte[] header, int headerSize) {
		AsyncSupplier<DataFormat,NoException> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<ZipArchive>,Exception> zip = ZipDataFormat.cache.open(data, this, priority, null, 0);
		if (zip == null) {
			sp.unblockSuccess(null);
			return sp;
		}
		zip.thenStart("Detecting Open Documents", priority, (Task<Void, Exception> t) -> {
			if (!zip.isSuccessful()) {
				sp.unblockSuccess(null);
				throw zip.getError();
			}
			try {
				int count = 0;
				AsyncSupplier<CharArrayStringBuffer,IOException> mimetypeRead = null;
				for (ZipArchive.ZippedFile file : zip.getResult().get().getZippedFiles()) {
					switch (file.getFilename()) {
					case "content.xml":
					case "meta.xml":
					case "styles.xml":
					case "settings.xml":
						count++;
						break;
					case "mimetype":
						AsyncSupplier<IO.Readable,IOException> uncompress = file.uncompress(zip.getResult().get(), priority);
						try (IO.Readable io = uncompress.blockResult(0)) {
							mimetypeRead = IOUtil.readFullyAsString(io, Charset.forName("ISO-8859-1"), priority);
						} catch (IOException e) {
							sp.unblockSuccess(null);
							return null;
						}
						break;
					}
					if (count == 4 && mimetypeRead != null) break;
				}
				if (count == 4) {
					if (mimetypeRead != null) {
						AsyncSupplier<CharArrayStringBuffer,IOException> task = mimetypeRead;
						mimetypeRead.onDone(new Runnable() {
							@Override
							public void run() {
								if (!task.isSuccessful()) {
									sp.unblockSuccess(null);
									return;
								}
								String type = task.getResult().asString();
								if (ArrayUtil.contains(OpenDocumentTextDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentTextDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentGraphicsDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentGraphicsDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentPresentationDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentPresentationDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentSpreadsheetDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentSpreadsheetDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentChartDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentChartDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentImageDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentImageDataFormat.instance);
								else if (ArrayUtil.contains(OpenDocumentFormulaDataFormat.mime, type))
									sp.unblockSuccess(OpenDocumentFormulaDataFormat.instance);
								else {
									// TODO application/vnd.oasis.opendocument.text-master (.odm)
									// TODO application/vnd.oasis.opendocument.text-web (.oth)
									LCCore.getApplication().getDefaultLogger().warn("Unknown OpenDocument MIME Type: "+type);
									sp.unblockSuccess(null);
								}
							}
						});
						return null;
					}
					// TODO analyze xml files ?
				}
				sp.unblockSuccess(null);
				return null;
			} catch (Exception e) {
				sp.unblockSuccess(null);
				throw e;
			} finally {
				zip.getResult().release(OpenOfficeDetector.this);
			}
		}, true);
		return sp;
	}
	
}
