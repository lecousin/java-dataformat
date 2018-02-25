package net.lecousin.dataformat.archive.cfb;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class CFBPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { CFBDataFormat.instance };
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] { new CFBDetector() };
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
		};
	}
	
}
