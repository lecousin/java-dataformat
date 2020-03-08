package net.lecousin.dataformat.core.operations;

import java.util.function.Supplier;

import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.mutable.MutableBoolean;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

/**
 * Operation from one object type to another.
 * The output is a Pair with the output as first object, and any type of object as second object.
 * The reason of the Pair is because some intermediate objects may need to be released by the release method.
 */
public interface Operation<Input,Output,Parameters> extends IOperation<Parameters>, IOperation.FromObject<Input>, IOperation.ToObject<Output> {

	public static interface OneToOne<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncSupplier<Pair<Output,Object>,? extends Exception> execute(Input input, Parameters params, Priority priority, WorkProgress progress, long work);
		public void release(Pair<Output,Object> output);
	}
	
	public static interface OneToMany<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.OneToMany<Input, Parameters, Output> {
	}
	
	public static interface ManyToOne<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.ManyToOne {
		/**
		 * Start the operation, which will call the inputProvider each time it needs a new input.
		 * The inputProvider must return an AsyncWork with a null result when no more input is available and the operation should end.
		 */
		public AsyncSupplier<Pair<Output,Object>, ? extends Exception> startOperation(Supplier<AsyncSupplier<Input,? extends Exception>> inputProvider, int nbInputs, Parameters params, Priority priority, WorkProgress progress, long work);
		public void release(Pair<Output,Object> output);
	}
	
	public static interface ManyToMany<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.ManyToMany<Input, Parameters, Output> {
	}
	
	@SuppressWarnings("rawtypes")
	public static class ManyToOneAsOneToOne implements OneToOne {
		public ManyToOneAsOneToOne(ManyToOne op) {
			this.op = op;
		}
		private ManyToOne op;
		@Override
		public Class getInputType() {
			return op.getInputType();
		}
		@Override
		public Class getOutputType() {
			return op.getOutputType();
		}
		@Override
		public ILocalizableString getOutputName() {
			return op.getOutputName();
		}
		@Override
		public IconProvider getOutputTypeIconProvider() {
			return op.getOutputTypeIconProvider();
		}
		@Override
		public ILocalizableString getName() {
			return op.getName();
		}
		@Override
		public Class getParametersClass() {
			return op.getParametersClass();
		}
		@Override
		public Object createDefaultParameters() {
			return op.createDefaultParameters();
		}
		@SuppressWarnings("unchecked")
		@Override
		public AsyncSupplier execute(Object input, Object params, Priority priority, WorkProgress progress, long work) {
			MutableBoolean inputGiven = new MutableBoolean(false);
			Supplier<AsyncSupplier> inputProvider = new Supplier<AsyncSupplier>() {
				@Override
				public AsyncSupplier get() {
					if (inputGiven.get())
						return new AsyncSupplier(null, null);
					inputGiven.set(true);
					return new AsyncSupplier(input, null);
				}
			};
			return op.startOperation(inputProvider, 1, params, priority, progress, work);
		}
		@SuppressWarnings("unchecked")
		@Override
		public void release(Pair output) {
			op.release(output);
		}
	}
	
}
