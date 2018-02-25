package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.archive.zip.ZipArchive;
import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.memory.CachedObject;

public class OfficeOpenXMLDetector implements DataFormatSpecializationDetector {

	@Override
	public DataFormat getBaseFormat() {
		return ZipDataFormat.instance;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			ExcelOpenXMLFormat.instance,
			WordOpenXMLFormat.instance,
			PowerPointOpenXMLFormat.instance
		};
	}
	
	@Override
	public AsyncWork<DataFormat,NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize) {
		AsyncWork<DataFormat,NoException> sp = new AsyncWork<>();
		AsyncWork<CachedObject<ZipArchive>,Exception> zip = ZipDataFormat.cache.open(data, this, priority, null, 0);
		if (zip == null) {
			sp.unblockSuccess(null);
			return sp;
		}
		zip.listenAsync(new Task.Cpu<Void,Exception>("Detecting Office Open XML", priority) {
			@Override
			public Void run() throws Exception {
				if (!zip.isSuccessful()) {
					sp.unblockSuccess(null);
					throw zip.getError();
				}
				try {
					boolean hasContentTypes = false, hasProperties = false;
					DataFormat format = null;
					for (ZipArchive.ZippedFile file : zip.getResult().get().getZippedFiles()) {
						if ("[Content_Types].xml".equals(file.getFilename()))
							hasContentTypes = true;
						else if ("docProps/core.xml".equals(file.getFilename()))
							hasProperties = true;
						else if ("xl/workbook.xml".equals(file.getFilename()))
							format = ExcelOpenXMLFormat.instance;
						else if ("word/document.xml".equals(file.getFilename()))
							format = WordOpenXMLFormat.instance;
						else if ("ppt/presentation.xml".equals(file.getFilename()))
							format = PowerPointOpenXMLFormat.instance;
						else
							continue;
						if (hasContentTypes && hasProperties && format != null) {
							sp.unblockSuccess(format);
							return null;
						}
					}
					sp.unblockSuccess(null);
					return null;
				} catch (Exception e) {
					sp.unblockSuccess(null);
					throw e;
				} finally {
					zip.getResult().release(OfficeOpenXMLDetector.this);
				}
			}
		}, true);
		return sp;
	}
	
}
