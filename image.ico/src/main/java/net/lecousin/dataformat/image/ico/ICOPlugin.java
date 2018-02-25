package net.lecousin.dataformat.image.ico;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class ICOPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { ICODataFormat.instance };
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new ICODetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
		};
	}
	
}
