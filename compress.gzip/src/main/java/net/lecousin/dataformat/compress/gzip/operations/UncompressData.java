package net.lecousin.dataformat.compress.gzip.operations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.compression.gzip.GZipReadable;
import net.lecousin.dataformat.compress.gzip.GZipDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataToDataOperation;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class UncompressData implements DataToDataOperation.OneToOne<GZipDataFormat, Void> {

	@Override
	public List<Class<? extends DataFormat>> getAcceptedInputs() {
		List<Class<? extends DataFormat>> list = new ArrayList<>(0);
		list.add(GZipDataFormat.class);
		return list;
	}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.compress", "Uncompress with", "GZip");
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
	public AsyncSupplier<Void, ? extends Exception> execute(Data input, Pair<Data, IO.Writable> output, Void params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<Void, IOException> result = new AsyncSupplier<>();
		input.openReadOnly(priority).onDone((inp) -> {
			@SuppressWarnings("resource")
			GZipReadable in = new GZipReadable(inp, priority);
			@SuppressWarnings("resource")
			IO.Writable out = output.getValue2();
			AsyncSupplier<Long, IOException> copy = IOUtil.copy(in, out, -1, false, progress, work);
			copy.onDone(() -> {
				in.closeAsync();
				if (copy.hasError()) {
					result.error(copy.getError());
					out.closeAsync();
				} else
					result.unblockSuccess(null);
			});
		}, result);
		return result;
	}

}
