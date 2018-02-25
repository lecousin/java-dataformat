package net.lecousin.dataformat.core.operations;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.mutable.MutableBoolean;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Provider;

/**
 * Operation from one object type to another.
 * The output is a Pair with the output as first object, and any type of object as second object.
 * The reason of the Pair is because some intermediate objects may need to be released by the release method.
 */
public interface Operation<Input,Output,Parameters> extends IOperation<Parameters>, IOperation.FromObject<Input>, IOperation.ToObject<Output> {

	public static interface OneToOne<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncWork<Pair<Output,Object>,? extends Exception> execute(Input input, Parameters params, byte priority, WorkProgress progress, long work);
		public void release(Pair<Output,Object> output);
	}
	
	public static interface OneToMany<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.OneToMany {
		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation
		 */
		public AsyncWork<Object, ? extends Exception> initOperation(Input input, Parameters params, byte priority, WorkProgress progress, long work);
		/** If known return the number of outputs that will be generated, or -1 if not known. */
		public int getNbOutputs(Object operation);
		/**
		 * Produce the next output. If the operation is finished, return an AsyncWork with a null result. 
		 */
		public AsyncWork<Output, ? extends Exception> nextOutput(Object operation, byte priority, WorkProgress progress, long work);
		
		public void releaseOutput(Output output);
		public void releaseOperation(Object operation);
	}
	
	public static interface ManyToOne<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.ManyToOne {
		/**
		 * Start the operation, which will call the inputProvider each time it needs a new input.
		 * The inputProvider must return an AsyncWork with a null result when no more input is available and the operation should end.
		 */
		public AsyncWork<Pair<Output,Object>, ? extends Exception> startOperation(Provider<AsyncWork<Input,? extends Exception>> inputProvider, int nbInputs, Parameters params, byte priority, WorkProgress progress, long work);
		public void release(Pair<Output,Object> output);
	}
	
	public static interface ManyToMany<Input,Output,Parameters> extends Operation<Input,Output,Parameters>, IOperation.ManyToMany {
		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation.
		 * The inputProvider each time it needs a new input.
		 * The inputProvider must return an AsyncWork with a null result when no more input is available and the operation should end.
		 */
		public AsyncWork<Object, ? extends Exception> initOperation(Provider<AsyncWork<Input,? extends Exception>> inputProvider, int nbInputs, Parameters params, byte priority, WorkProgress progress, long work);
		/** If known return the number of outputs that will be generated, or -1 if not known. */
		public int getNbOutputs(Object operation);
		/**
		 * Produce the next output. If the operation is finished, return an AsyncWork with a null result. 
		 */
		public AsyncWork<Output, ? extends Exception> nextOutput(Object operation, byte priority, WorkProgress progress, long work);
		
		public void releaseOutput(Output output);
		public void releaseOperation(Object operation);
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
		public AsyncWork execute(Object input, Object params, byte priority, WorkProgress progress, long work) {
			MutableBoolean inputGiven = new MutableBoolean(false);
			Provider<AsyncWork> inputProvider = new Provider<AsyncWork>() {
				@Override
				public AsyncWork provide() {
					if (inputGiven.get())
						return new AsyncWork(null, null);
					inputGiven.set(true);
					return new AsyncWork(input, null);
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
