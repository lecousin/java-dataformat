package net.lecousin.dataformat.compress.gzip.operations;

import java.io.IOException;
import java.util.List;

import net.lecousin.compression.gzip.GZipWritable;
import net.lecousin.dataformat.compress.gzip.GZipDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataToDataOperation;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class CompressData implements DataToDataOperation.OneToOne<GZipDataFormat, Void> {

	@Override
	public List<Class<? extends DataFormat>> getAcceptedInputs() {
		return null;
	}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.compress", "Compress with", "GZip");
	}

	@Override
	public Class<Void> getParametersClass() { return Void.class; }

	@Override
	public Void createDefaultParameters() {
		return null;
	}

	@Override
	public GZipDataFormat getOutputFormat() { return GZipDataFormat.instance; }

	@Override
	public AsyncWork<Void, ? extends Exception> execute(Data input, Pair<Data, IO.Writable> output, Void params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Void, Exception> result = new AsyncWork<>();
		input.openReadOnly(priority).listenInline((in) -> {
			@SuppressWarnings("resource")
			GZipWritable out = new GZipWritable(output.getValue2(), priority, 9, 5);
			AsyncWork<Long, IOException> copy = IOUtil.copy(in, out, input.getSize(), false, progress, work);
			copy.listenInline(() -> {
				in.closeAsync();
				if (copy.hasError()) {
					result.error(copy.getError());
					out.closeAsync();
				} else
					out.finishAsync().listenInline(
						() -> { result.unblockSuccess(null); },
						(error) -> { result.error(error); },
						(cancel) -> { result.cancel(cancel); }
					);
			});
		}, result);
		return result;
	}

}
