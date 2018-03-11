package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class VirtualBoxPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { VDIDataFormat.instance, SAVDataFormat.instance };
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] { new VDIDetector(), new SAVDetector() };
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
