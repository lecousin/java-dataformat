package net.lecousin.dataformat.image.png;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class PNGPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { PNGDataFormat.instance };
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new PNGDetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
			new PNGReaderOp(),
			new PNGWriterOp()
		};
	}
	
}
