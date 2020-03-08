package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;

public interface DataFormatSpecializationDetector {

	public DataFormat getBaseFormat();
	public DataFormat[] getDetectedFormats();
	
	public AsyncSupplier<DataFormat,NoException> detectSpecialization(Data data, Priority priority, byte[] header, int headerSize);
	
}
