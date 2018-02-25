package net.lecousin.dataformat.core;

import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.framework.plugins.Plugin;

public interface DataFormatPlugin extends Plugin {

	public DataFormat[] getFormats();
	public DataFormatDetector[] getDetectors();
	public DataFormatSpecializationDetector[] getSpecializationDetectors();
	
	public IOperation<?>[] getOperations();
	
}
