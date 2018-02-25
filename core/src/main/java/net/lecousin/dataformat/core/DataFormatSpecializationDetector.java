package net.lecousin.dataformat.core;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;

public interface DataFormatSpecializationDetector {

	public DataFormat getBaseFormat();
	public DataFormat[] getDetectedFormats();
	
	public AsyncWork<DataFormat,NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize);
	
}
