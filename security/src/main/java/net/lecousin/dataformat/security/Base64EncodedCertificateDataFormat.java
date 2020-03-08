package net.lecousin.dataformat.security;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataWrapperDataFormat;
import net.lecousin.dataformat.core.MemoryData;
import net.lecousin.dataformat.text.TextDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.encoding.Base64Encoding;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;

public class Base64EncodedCertificateDataFormat extends TextDataFormat implements DataWrapperDataFormat {

	public static final Base64EncodedCertificateDataFormat instance = new Base64EncodedCertificateDataFormat();
	
	private Base64EncodedCertificateDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Base64 encoded certificate");
	}

	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return DataCommonProperties.class;
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		DataCommonProperties p = new DataCommonProperties();
		p.size = BigInteger.valueOf(subData.getSize());
		return p;
	}

	@Override
	public AsyncSupplier<Data, Exception> getWrappedData(Data container, WorkProgress progress, long work) {
		long size = container.getSize();
		if (size > 4 * 1024 * 1024)
			return new AsyncSupplier<>(null, new Exception("Certificate is too big"));
		AsyncSupplier<? extends IO, IOException> open = container.openReadOnly(Task.Priority.NORMAL);
		byte[] buffer = new byte[(int)size];
		AsyncSupplier<Data, Exception> result = new AsyncSupplier<>();
		long stepOpen = work / 20;
		long stepRead = work * 3 / 4;
		long stepDecode = work - stepOpen - stepRead;
		open.onDone((io) -> {
			progress.progress(stepOpen);
			AsyncSupplier<Integer, IOException> read = ((IO.Readable)io).readFullyAsync(ByteBuffer.wrap(buffer));
			read.thenStart(Task.cpu("Extract certificate", Task.Priority.NORMAL, (Task<Void, NoException> t) -> {
				progress.progress(stepRead);
				io.closeAsync();
				// search end of line
				int start = 0;
				while (buffer[start++] != '\n') {
					if (start == buffer.length) {
						result.error(new Exception("Invalid certificate"));
						return null;
					}
				}
				int end = start;
				while (buffer[end] != '-') {
					if (++end == buffer.length) break;
				}
				// remove end of line
				int removed = 0;
				for (int i = start; i < end; ++i) {
					if (buffer[i] == '\r' || buffer[i] == '\n')
						removed++;
					else if (removed > 0)
						buffer[i - removed] = buffer[i];
				}
				progress.progress(stepDecode / 2);
				try {
					byte[] decoded = Base64Encoding.instance.decode(buffer, start, end - removed - start);
					progress.progress(stepDecode - stepDecode / 2);
					// TODO localize
					result.unblockSuccess(new MemoryData(container, decoded, new FixedLocalizedString("Certificate")));
				} catch (Exception e) {
					result.error(e);
				}
				return null;
			}), result, e -> e);
		}, result, e -> e);
		result.onDone(
			(data) -> {
			},
			(error) -> {
				progress.error(error);
			},
			(cancel) -> {
				progress.cancel(cancel);
			}
		);
		return result;
	}
	
	// TODO mime, extensions...
	
}
