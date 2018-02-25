package net.lecousin.dataformat.core.operations.chain;

import java.io.File;
import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.file.FileData;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.core.operations.DataToDataOperation;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.locale.CompositeLocalizable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.CompositeNamedObject;
import net.lecousin.framework.util.Pair;

public class DataFormatReadOperationOneToOneWithConversion<Input extends DataFormat, Output> implements DataFormatReadOperation.OneToOne<Input, Output, CompositeNamedObject> {

	@SuppressWarnings("unchecked")
	public <Intermediate extends DataFormat> DataFormatReadOperationOneToOneWithConversion(Input inputFormat, DataToDataOperation.OneToOne<Intermediate, ?> conversion, DataFormatReadOperation.OneToOne<Intermediate, Output, ?> read) {
		this.inputFormat = inputFormat;
		this.conversion = (DataToDataOperation.OneToOne<?, Object>)conversion;
		this.read = (DataFormatReadOperation.OneToOne<?, Output, Object>)read;
	}

	private Input inputFormat;
	private DataToDataOperation.OneToOne<?, Object> conversion;
	private DataFormatReadOperation.OneToOne<?, Output, Object> read;
	
	@Override
	public ILocalizableString getName() {
		return new CompositeLocalizable(", ", conversion.getName(), read.getName());
	}
	@Override
	public Class<CompositeNamedObject> getParametersClass() {
		return CompositeNamedObject.class;
	}
	
	@Override
	public CompositeNamedObject createDefaultParameters() {
		CompositeNamedObject o = new CompositeNamedObject();
		o.add(conversion.getName(), conversion.createDefaultParameters());
		o.add(read.getName(), read.createDefaultParameters());
		return o;
	}
	
	@Override
	public Input getInputFormat() {
		return inputFormat;
	}
	
	@Override
	public Class<Output> getOutputType() {
		return read.getOutputType();
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return read.getOutputTypeIconProvider();
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return read.getOutputName();
	}
	
	@SuppressWarnings("resource")
	@Override
	public AsyncWork<Pair<Output, Object>, Exception> execute(Data data, CompositeNamedObject params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Pair<Output, Object>, Exception> result = new AsyncWork<>();
		long stepConversion = work*75/100;
		long stepRead = work - stepConversion;
		File tmpFile;
		// TODO use TemporaryFiles.createFileAsync
		try { tmpFile = File.createTempFile("dataoperation", ""); }
		catch (IOException e) {
			result.error(e);
			return result;
		}
		tmpFile.deleteOnExit();
		FileData tmpData = FileData.get(tmpFile);
		FileIO.WriteOnly out = new FileIO.WriteOnly(tmpFile, priority);
		AsyncWork<Void,? extends Exception> conv = conversion.execute(data, new Pair<>(tmpData, out), params.get(0), priority, progress, stepConversion);
		conv.listenInline(new Runnable() {
			@Override
			public void run() {
				try { out.close(); }
				catch (Exception e) {}
				if (conv.hasError()) { result.error(conv.getError()); return; }
				if (conv.isCancelled()) { result.cancel(conv.getCancelEvent()); return; }
				AsyncWork<Pair<Output, Object>, ? extends Exception> r = read.execute(tmpData, params.get(1), priority, progress, stepRead);
				r.listenInline(new Runnable() {
					@Override
					public void run() {
						tmpFile.delete();
						if (r.hasError()) { result.error(r.getError()); return; }
						if (r.isCancelled()) { result.cancel(r.getCancelEvent()); return; }
						result.unblockSuccess(new Pair<>(r.getResult().getValue1(), new Pair<>(tmpData, r.getResult().getValue2())));
					}
				});
			}
		});
		return result;
	}
	
	@Override
	public void release(Data data, Pair<Output, Object> output) {
		@SuppressWarnings("unchecked")
		Pair<Data, Object> p = (Pair<Data, Object>)output.getValue2();
		read.release(p.getValue1(), new Pair<>(output.getValue1(), p.getValue2()));
	}
	
}
