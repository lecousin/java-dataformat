package net.lecousin.dataformat.microsoft;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.microsoft.dev.MSFTDataFormat;
import net.lecousin.dataformat.microsoft.dev.MSFTDetector;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySetStream;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySetStreamDetector;

public class MicrosoftPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			OLEPropertySetStream.instance,
			MSFTDataFormat.instance
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new OLEPropertySetStreamDetector(),
			new MSFTDetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
		};
	}
	
}
