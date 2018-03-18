package net.lecousin.dataformat.core;

import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.framework.plugins.Plugin;

public interface DataFormatPlugin extends Plugin {

	public DataFormat[] getFormats();
	public DataFormatDetector[] getDetectors();
	public DataFormatSpecializationDetector[] getSpecializationDetectors();
	
	public DataAction[] getActions();
	public IOperation<?>[] getOperations();
	
}
