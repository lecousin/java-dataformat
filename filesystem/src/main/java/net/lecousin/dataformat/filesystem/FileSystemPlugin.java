package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class FileSystemPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			MBRDataFormat.instance,
			PhysicalDrivesDataFormat.instance
		};
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new MBRDetector()
		};
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
		};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
		};
	}

}
