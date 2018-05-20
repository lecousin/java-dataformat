package net.lecousin.dataformat.security;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataWrapperDataFormat;
import net.lecousin.dataformat.core.MemoryData;
import net.lecousin.dataformat.text.TextDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.encoding.Base64;
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
	public AsyncWork<Data, Exception> getWrappedData(Data container, WorkProgress progress, long work) {
		long size = container.getSize();
		if (size > 4 * 1024 * 1024)
			return new AsyncWork<>(null, new Exception("Certificate is too big"));
		AsyncWork<? extends IO, Exception> open = container.openReadOnly(Task.PRIORITY_NORMAL);
		byte[] buffer = new byte[(int)size];
		AsyncWork<Data, Exception> result = new AsyncWork<>();
		long stepOpen = work / 20;
		long stepRead = work * 3 / 4;
		long stepDecode = work - stepOpen - stepRead;
		open.listenInline((io) -> {
			progress.progress(stepOpen);
			AsyncWork<Integer, IOException> read = ((IO.Readable)io).readFullyAsync(ByteBuffer.wrap(buffer));
			read.listenAsyncSP(new Task.Cpu.FromRunnable("Extract certificate", Task.PRIORITY_NORMAL, () -> {
				progress.progress(stepRead);
				io.closeAsync();
				// search end of line
				int start = 0;
				while (buffer[start++] != '\n') {
					if (start == buffer.length) {
						result.error(new Exception("Invalid certificate"));
						return;
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
					byte[] decoded = Base64.decode(buffer, start, end - removed - start);
					progress.progress(stepDecode - stepDecode / 2);
					// TODO localize
					result.unblockSuccess(new MemoryData(container, decoded, new FixedLocalizedString("Certificate")));
				} catch (Exception e) {
					result.error(e);
				}
			}), result);
		}, result);
		result.listenInline(
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
