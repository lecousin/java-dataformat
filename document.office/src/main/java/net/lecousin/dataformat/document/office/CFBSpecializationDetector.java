package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.archive.cfb.CFBDataFormat;
import net.lecousin.dataformat.archive.cfb.CFBFile;
import net.lecousin.dataformat.archive.cfb.CFBFile.CFBSubFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.memory.CachedObject;

public class CFBSpecializationDetector implements DataFormatSpecializationDetector {

	@Override
	public DataFormat getBaseFormat() {
		return CFBDataFormat.instance;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			ExcelFile_CFB_BIFF5_7_DataFormat.instance,
			ExcelFile_CFB_BIFF8_DataFormat.instance,
			PowerPointFile_CFB_DataFormat.instance,
			VisioFile_CFB_DataFormat.instance,
			WordFile_CFB_DataFormat.instance,
		};
	}
	
	@Override
	public AsyncSupplier<DataFormat,NoException> detectSpecialization(Data data, Priority priority, byte[] header, int headerSize) {
		AsyncSupplier<CachedObject<CFBFile>,Exception> cfb = CFBDataFormat.cache.open(data, this, priority/*, true*/, null, 0);
		AsyncSupplier<DataFormat,NoException> sp = new AsyncSupplier<>();
		Task<DataFormat,NoException> task = Task.cpu("Detect MS Office format in CFB", priority, t -> {
			if (!cfb.isSuccessful()) {
				t.getApplication().getDefaultLogger().error("Error opening CFB file", cfb.getError());
				return null;
			}
			try {
				for (CFBSubFile file : cfb.getResult().get().getContent()) {
					String name = file.getName();
					if ("WordDocument".equals(name))
						return WordFile_CFB_DataFormat.instance;
					if ("Workbook".equals(name))
						return ExcelFile_CFB_BIFF8_DataFormat.instance;
					if ("Book".equals(name))
						return ExcelFile_CFB_BIFF5_7_DataFormat.instance;
					if ("PowerPoint Document".equals(name))
						return PowerPointFile_CFB_DataFormat.instance;
					if ("VisioDocument".equals(name))
						return VisioFile_CFB_DataFormat.instance;
				}
				return null;
			} finally {
				cfb.getResult().release(CFBSpecializationDetector.this);
			}
		});
		task.startOn(cfb, true);
		task.getOutput().onDone(new Runnable() {
			@Override
			public void run() {
				sp.unblockSuccess(task.getOutput().getResult());
			}
		});
		return sp;
	}
	
}
