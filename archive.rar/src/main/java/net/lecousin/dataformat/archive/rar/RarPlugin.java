package net.lecousin.dataformat.archive.rar;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class RarPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			RarDataFormat.instance
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new RarDetector.OldFormat(),
			new RarDetector.Version1_5(),
			new RarDetector.Version5()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {};
	}
	
}
