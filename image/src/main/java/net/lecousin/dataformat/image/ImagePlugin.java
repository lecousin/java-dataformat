package net.lecousin.dataformat.image;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.image.operations.ScaleImage;

public class ImagePlugin implements DataFormatPlugin {

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[0];
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[0];
	}

	@Override
	public DataAction[] getActions() {
		return new DataAction[0];
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
			new ScaleImage()
		};
	}
	
}
