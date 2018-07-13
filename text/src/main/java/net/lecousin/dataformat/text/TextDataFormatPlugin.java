package net.lecousin.dataformat.text;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.text.csv.DelimiterSeaparatedValuesFormat;
import net.lecousin.dataformat.text.xml.XMLDataFormat;

public class TextDataFormatPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			TextDataFormat.instance,
			DelimiterSeaparatedValuesFormat.instance,
			XMLDataFormat.instance
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
