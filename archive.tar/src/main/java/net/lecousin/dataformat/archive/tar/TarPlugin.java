package net.lecousin.dataformat.archive.tar;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;

public class TarPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			TarDataFormat.instance
		};
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new TarDetector()
		};
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {};
	}

	@Override
	public DataAction[] getActions() {
		return new DataAction[0];
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {};
	}

}
