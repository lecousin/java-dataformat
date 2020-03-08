package net.lecousin.dataformat.executable.windows.msdos;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;

public class MZSpecializationDetector implements DataFormatSpecializationDetector {

	@Override
	public DataFormat getBaseFormat() {
		return MZDataFormat.instance;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			MZCompressedDataFormat.PKLITE.instance	,
			MZCompressedDataFormat.LZ.instance
		};
	}
	
	@Override
	public AsyncSupplier<DataFormat, NoException> detectSpecialization(Data data, Priority priority, byte[] header, int headerSize) {
		if (headerSize >= 0x20) {
			if (header[0x1C] == 'L' &&
				header[0x1D] == 'Z' &&
				((header[0x1E] == '0' && header[0x1F] == '9') || // version 0.90
				 (header[0x1E] == '9' && header[0x1F] == '1')) // version 0.91
			)
				return new AsyncSupplier<>(MZCompressedDataFormat.LZ.instance, null);

			if (headerSize >= 0x23) {
				if (header[0x1E] == 'P' &&
					header[0x1F] == 'K' &&
					header[0x20] == 'L' &&
					header[0x21] == 'I' &&
					header[0x22] == 'T' &&
					header[0x23] == 'E'
				)
					return new AsyncSupplier<>(MZCompressedDataFormat.PKLITE.instance, null);
			}
		}
		return null;
	}
	
}
