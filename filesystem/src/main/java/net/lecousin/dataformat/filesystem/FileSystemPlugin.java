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
			EFIPartDataFormat.instance,
			MBRLegacyToEFIPartDataFormat.instance,
			PhysicalDrivesDataFormat.instance
		};
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new MBRDetector(),
			new EFIPartDetector()
		};
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
			new MBRLegacyToEFIPartDetector()
		};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
		};
	}

}
