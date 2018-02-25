package net.lecousin.dataformat.security;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class SecurityDataFormatPlugin implements DataFormatPlugin {

	// http://www.cryptosys.net/pki/rsakeyformats.html
	
	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			PEMDataFormat.instance
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
		};
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {};
	}
	
}
