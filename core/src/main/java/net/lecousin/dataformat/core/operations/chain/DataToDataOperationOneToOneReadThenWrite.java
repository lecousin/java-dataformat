package net.lecousin.dataformat.core.operations.chain;

import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.dataformat.core.operations.DataToDataOperation;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.CompositeLocalizable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.CompositeNamedObject;
import net.lecousin.framework.util.Pair;

public class DataToDataOperationOneToOneReadThenWrite<Input extends DataFormat, Output extends DataFormat> implements DataToDataOperation.OneToOne<Output, CompositeNamedObject> {

	@SuppressWarnings("unchecked")
	public <Intermediate> 
	DataToDataOperationOneToOneReadThenWrite(
		DataFormatReadOperation.OneToOne<Input, Intermediate, ?> read,
		DataFormatWriteOperation.OneToOne<Intermediate, Output, ?> write
	) {
		this.read = (DataFormatReadOperation.OneToOne<Input, Object, Object>)read;
		this.write = (DataFormatWriteOperation.OneToOne<Object, Output, Object>)write;
	}
	
	private DataFormatReadOperation.OneToOne<Input, Object, Object> read;
	private DataFormatWriteOperation.OneToOne<Object, Output, Object> write;
	
	@Override
	public List<Class<? extends DataFormat>> getAcceptedInputs() {
		return Collections.singletonList(read.getInputFormat().getClass());
	}
	
	@Override
	public ILocalizableString getName() {
		return new CompositeLocalizable(", ", read.getName(), write.getName());
	}
	
	@Override
	public Class<CompositeNamedObject> getParametersClass() {
		return CompositeNamedObject.class;
	}
	
	@Override
	public CompositeNamedObject createDefaultParameters() {
		CompositeNamedObject o = new CompositeNamedObject();
		o.add(read.getName(), read.createDefaultParameters());
		o.add(write.getName(), write.createDefaultParameters());
		return o;
	}

	@Override
	public Output getOutputFormat() {
		return write.getOutputFormat();
	}
	
	@Override
	public AsyncSupplier<Void, Exception> execute(Data input, Pair<Data, IO.Writable> output, CompositeNamedObject params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<Void, Exception> result = new AsyncSupplier<>();
		AsyncSupplier<Pair<Object,Object>, ? extends Exception> r = read.execute(input, params.get(0), priority, progress, work/2);
		r.onDone(new Runnable() {
			@Override
			public void run() {
				if (r.hasError()) { result.error(r.getError()); return; }
				if (r.isCancelled()) { result.cancel(r.getCancelEvent()); return; }
				AsyncSupplier<Void,? extends Exception> w = write.execute(r.getResult().getValue1(), output, params.get(1), priority, progress, work-(work/2));
				w.onDone(new Runnable() {
					@Override
					public void run() {
						read.release(input, r.getResult());
						if (w.hasError()) { result.error(w.getError()); return; }
						if (w.isCancelled()) { result.cancel(w.getCancelEvent()); return; }
						result.unblockSuccess(null);
					}
				});
			}
		});
		return result;
	}
	
}
