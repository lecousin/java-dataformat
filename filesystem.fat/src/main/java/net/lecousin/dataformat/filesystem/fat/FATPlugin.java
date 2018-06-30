package net.lecousin.dataformat.filesystem.fat;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.filesystem.fat.FATDetector.FAT12Detector;
import net.lecousin.dataformat.filesystem.fat.FATDetector.FAT16Detector;
import net.lecousin.dataformat.filesystem.fat.FATDetector.FAT32Detector;

public class FATPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			FATDataFormat.FAT12DataFormat.instance,
			FATDataFormat.FAT16DataFormat.instance,
			FATDataFormat.FAT32DataFormat.instance,
		};
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new FAT12Detector(),
			new FAT16Detector(),
			new FAT32Detector(),
		};
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
