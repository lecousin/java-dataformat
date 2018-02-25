package net.lecousin.dataformat.core.operations.chain;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.locale.CompositeLocalizable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.CompositeNamedObject;
import net.lecousin.framework.util.Pair;

public class DataFormatReadOperationOneToOneWithIntermediates<Input extends DataFormat, Output> implements DataFormatReadOperation.OneToOne<Input, Output, CompositeNamedObject> {

	public DataFormatReadOperationOneToOneWithIntermediates(DataFormatReadOperation.OneToOne<Input, ?, ?> read, Operation.OneToOne<?, Output, ?> transform) {
		this.read = read;
		this.transform = transform;
	}
	
	private DataFormatReadOperation.OneToOne<Input, ?, ?> read;
	private Operation.OneToOne<?, Output, ?> transform;
	
	@Override
	public ILocalizableString getName() {
		return new CompositeLocalizable(", ", read.getName(), transform.getName());
	}
	
	@Override
	public Input getInputFormat() { return read.getInputFormat(); }
	@Override
	public Class<Output> getOutputType() { return transform.getOutputType(); }
	
	@Override
	public ILocalizableString getOutputName() {
		return transform.getOutputName();
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return transform.getOutputTypeIconProvider();
	}
	
	@Override
	public Class<CompositeNamedObject> getParametersClass() {
		return CompositeNamedObject.class;
	}
	
	@Override
	public CompositeNamedObject createDefaultParameters() {
		CompositeNamedObject o = new CompositeNamedObject();
		o.add(read.getName(), read.createDefaultParameters());
		o.add(transform.getName(), transform.createDefaultParameters());
		return o;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public AsyncWork<Pair<Output, Object>, ? extends Exception> execute(Data data, CompositeNamedObject params, byte priority, WorkProgress progress, long work) {
		long stepRead;
		if (transform instanceof OperationOneToOneChain)
			stepRead = work / (((OperationOneToOneChain)transform).getChain().size() + 1);
		else
			stepRead = work / 2;
		long stepTransform = work - stepRead;
		
		AsyncWork<Pair<Output,Object>,Exception> sp = new AsyncWork<>();
		
		((DataFormatReadOperation.OneToOne)read).execute(data, params.get(0), priority, progress, stepRead).listenInline(new AsyncWorkListener() {
			@Override
			public void ready(Object result) {
				Pair p = (Pair)result;
				((Operation.OneToOne)transform).execute(p.getValue1(), params.get(1), priority, progress, stepTransform).listenInline(new AsyncWorkListener() {
					@Override
					public void ready(Object result) {
						Pair p2 = (Pair)result;
						sp.unblockSuccess(new Pair<>((Output)p2.getValue1(), new Pair(p, p2)));
					}
					@Override
					public void error(Exception error) {
						read.release(data, p);
						sp.error(error);
					}
					@Override
					public void cancelled(CancelException event) {
						read.release(data, p);
						sp.cancel(event);
					}
				});
			}
			@Override
			public void error(Exception error) {
				sp.error(error);
			}
			@Override
			public void cancelled(CancelException event) {
				sp.cancel(event);
			}
		});
		return sp;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void release(Data data, Pair<Output, Object> output) {
		Pair p = (Pair)output.getValue2();
		read.release(data, (Pair)p.getValue1());
		transform.release((Pair)p.getValue2());
	}

}
