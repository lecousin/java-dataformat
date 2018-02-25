package net.lecousin.dataformat.core.operations.chain;

import java.util.ArrayList;
import java.util.LinkedList;

import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.locale.CompositeLocalizable;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.mutable.Mutable;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.CompositeNamedObject;
import net.lecousin.framework.util.Pair;

public class OperationOneToOneChain<Input,Output> implements Operation.OneToOne<Input,Output,CompositeNamedObject> {

	@SuppressWarnings({ "cast", "unchecked" })
	public static <Input,Intermediate,Output> OperationOneToOneChain<Input,Output> create(Operation.OneToOne<Input,Intermediate,?> op1, Operation.OneToOne<Intermediate,Output,?> op2) {
		return (OperationOneToOneChain<Input,Output>)createRaw(op1, op2);
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static OperationOneToOneChain createRaw(Operation.OneToOne op1, Operation.OneToOne op2) {
		OperationOneToOneChain chain = new OperationOneToOneChain();
		if (op1 instanceof OperationOneToOneChain) {
			for (Operation.OneToOne<?,?,?> op : ((OperationOneToOneChain<?,?>)op1).chain)
				chain.chain.add(op);
		} else
			chain.chain.add(op1);
		if (op2 instanceof OperationOneToOneChain) {
			for (Operation.OneToOne<?,?,?> op : ((OperationOneToOneChain<?,?>)op2).chain)
				chain.chain.add(op);
		} else
			chain.chain.add(op2);
		return chain;
	}
	
	private OperationOneToOneChain() {}
	
	private LinkedList<Operation.OneToOne<?,?,?>> chain = new LinkedList<>();
	
	public LinkedList<Operation.OneToOne<?,?,?>> getChain() {
		return chain;
	}
	
	@Override
	public ILocalizableString getName() {
		ILocalizableString[] list = new ILocalizableString[chain.size()];
		int i = 0;
		for (Operation.OneToOne<?,?,?> op : chain)
			list[i++] = op.getName();
		return new CompositeLocalizable(", ", list);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Class<Input> getInputType() { return (Class<Input>)chain.getFirst().getInputType(); }
	@SuppressWarnings("unchecked")
	@Override
	public Class<Output> getOutputType() { return (Class<Output>)chain.getLast().getOutputType(); }
	
	@Override
	public ILocalizableString getOutputName() {
		return chain.getLast().getOutputName();
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return chain.getLast().getOutputTypeIconProvider();
	}
	
	@Override
	public Class<CompositeNamedObject> getParametersClass() {
		return CompositeNamedObject.class;
	}
	
	@Override
	public CompositeNamedObject createDefaultParameters() {
		CompositeNamedObject o = new CompositeNamedObject();
		for (Operation.OneToOne<?,?,?> op : chain)
			o.add(op.getName(), op.createDefaultParameters());
		return o;
	}
	
	@Override
	public AsyncWork<Pair<Output,Object>,Exception> execute(Input input, CompositeNamedObject params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Pair<Output,Object>,Exception> result = new AsyncWork<>();
		
		MutableInteger index = new MutableInteger(0);
		Mutable<Object> in = new Mutable<>(input);
		MutableLong w = new MutableLong(work);
		@SuppressWarnings("rawtypes")
		ArrayList<Pair> outputs = new ArrayList<>(chain.size());
		new Runnable() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public void run() {
				int i = index.get();
				index.inc();
				long step = w.get() / (chain.size() - i);
				w.sub(step);
				((Operation.OneToOne)chain.get(i)).execute(in.get(), params.get(i), priority, progress, step).listenInline(new AsyncWorkListener() {
					@Override
					public void ready(Object r) {
						Pair p = (Pair)r;
						outputs.add(p);
						if (index.get() == chain.size()) {
							result.unblockSuccess(new Pair<>((Output)p.getValue1(), outputs));
							return;
						}
						run();
					}
					@Override
					public void error(Exception error) {
						for (int i = 0; i < outputs.size(); ++i)
							chain.get(i).release(outputs.get(i));
						result.unblockError(error);
					}
					@Override
					public void cancelled(CancelException event) {
						for (int i = 0; i < outputs.size(); ++i)
							chain.get(i).release(outputs.get(i));
						result.unblockCancel(event);
					}
				});
			}
		}.run();
		
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void release(Pair<Output, Object> output) {
		ArrayList<Pair> outputs = (ArrayList<Pair>)output.getValue2();
		for (int i = 0; i < outputs.size(); ++i)
			chain.get(i).release(outputs.get(i));
	}
	
}
