package net.lecousin.dataformat.image.bmp;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;

public class BMPPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { BMPDataFormat.instance, DIBDataFormat.instance };
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new BMPDetector(),
			new DIBDetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}

	@Override
	public DataAction[] getActions() {
		return new DataAction[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
			new BMPReaderOp(),
			new BMPWriterOp(),
			new DIBReaderOp()
		};
	}
	
}
