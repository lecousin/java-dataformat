package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;

public class FileSystemPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
		};
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
		};
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
		};
	}

	@Override
	public DataAction[] getActions() {
		return new DataAction[] {
		};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
		};
	}

}
