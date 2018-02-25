package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.archive.cfb.CFBDataFormat;
import net.lecousin.dataformat.archive.cfb.CFBFile;
import net.lecousin.dataformat.archive.cfb.CFBFile.CFBSubFile;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	public AsyncWork<DataFormat,NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize) {
		AsyncWork<CachedObject<CFBFile>,Exception> cfb = CFBDataFormat.cache.open(data, this, priority/*, true*/, null, 0);
		AsyncWork<DataFormat,NoException> sp = new AsyncWork<>();
		Task<DataFormat,NoException> task = new Task.Cpu<DataFormat,NoException>("Detect MS Office format in CFB", priority) {
			@Override
			public DataFormat run() {
				if (!cfb.isSuccessful()) {
					getApplication().getDefaultLogger().error("Error opening CFB file", cfb.getError());
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
			}
		};
		task.startOn(cfb, true);
		task.getOutput().listenInline(new Runnable() {
			@Override
			public void run() {
				sp.unblockSuccess(task.getResult());
			}
		});
		return sp;
	}
	
}
