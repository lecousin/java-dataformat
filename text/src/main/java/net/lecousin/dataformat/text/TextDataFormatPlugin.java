package net.lecousin.dataformat.text;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class TextDataFormatPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			TextDataFormat.instance,
			DelimiterSeaparatedValuesFormat.instance
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new TextDetector(),
			new UTF8Detector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {};
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {};
	}

}
