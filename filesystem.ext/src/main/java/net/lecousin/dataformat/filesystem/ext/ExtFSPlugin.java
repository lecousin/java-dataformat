package net.lecousin.dataformat.filesystem.ext;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class ExtFSPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { ExtFSDataFormat.instance };
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] { new ExtFSDetector() };
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[0];
	}

}
