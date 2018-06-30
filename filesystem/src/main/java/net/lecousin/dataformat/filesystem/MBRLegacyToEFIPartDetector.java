package net.lecousin.dataformat.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.system.hardware.DiskPartition;

public class MBRLegacyToEFIPartDetector implements DataFormatSpecializationDetector {

	@Override
	public DataFormat getBaseFormat() {
		return MBRDataFormat.instance;
	}

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			MBRLegacyToEFIPartDataFormat.instance
		};
	}

	@Override
	public AsyncWork<DataFormat, NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize) {
		AsyncWork<DataFormat, NoException> result = new AsyncWork<>();
		MBRDataFormat.instance.listenSubData(data, new CollectionListener<Data>() {
			@Override
			public void error(Throwable error) {
				result.unblockSuccess(null);
			}
			
			@Override
			public void elementsReady(Collection<? extends Data> elements) {
				if (elements.size() == 1) {
					Data d = elements.iterator().next();
					DiskPartition p = (DiskPartition)d.getProperty(MBRDataFormat.DATA_PROPERTY_PARTITION);
					if (p != null) {
						if (p.type == 0xEE) {
							AsyncWork<? extends IO.Readable.Buffered, Exception> open = d.openReadOnly(priority);
							byte[] buf = new byte[8];
							open.listenInline(() -> {
								if (!open.isSuccessful()) {
									result.unblockSuccess(null);
									return;
								}
								AsyncWork<Integer, IOException> read = open.getResult().readFullyAsync(ByteBuffer.wrap(buf));
								read.listenInline(() -> {
									if (!read.isSuccessful() || read.getResult().intValue() != 8) {
										result.unblockSuccess(null);
										return;
									}
									result.unblockSuccess(ArrayUtil.equals(EFIPartDetector.EFI_HEADER_SIGNATURE, buf) ? MBRLegacyToEFIPartDataFormat.instance : null);
								});
							});
							return;
						}
					}
				}
				result.unblockSuccess(null);
			}
			
			@Override
			public void elementsRemoved(Collection<? extends Data> elements) {
			}
			
			@Override
			public void elementsChanged(Collection<? extends Data> elements) {
			}
			
			@Override
			public void elementsAdded(Collection<? extends Data> elements) {
			}
		});
		return result;
	}

}
