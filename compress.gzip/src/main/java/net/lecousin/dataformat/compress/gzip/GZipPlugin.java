package net.lecousin.dataformat.compress.gzip;

import net.lecousin.dataformat.compress.gzip.operations.CompressData;
import net.lecousin.dataformat.compress.gzip.operations.UncompressData;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class GZipPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { GZipDataFormat.instance };
	}

	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] { new GZipDetector() };
	}

	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
			new CompressData(),
			new UncompressData()
		};
	}

}
