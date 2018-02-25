package net.lecousin.dataformat.core.operations;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Provider;

/**
 * Operation that takes one or more objects in input (memory representation), and produces a data in a specific format.
 */
public interface DataFormatWriteOperation<Input, Output extends DataFormat, Parameters> extends IOperation<Parameters>, IOperation.FromObject<Input>, IOperation.ToData<Output> {

	public static interface OneToOne<Input, Output extends DataFormat, Parameters> extends DataFormatWriteOperation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncWork<Void,? extends Exception> execute(Input input, Pair<Data,IO.Writable> output, Parameters params, byte priority, WorkProgress progress, long work);
	}
	
	public static interface ManyToOne<Input, Output extends DataFormat, Parameters> extends DataFormatWriteOperation<Input,Output,Parameters>, IOperation.ManyToOne {
		/**
		 * Start the operation, which will call the inputProvider each time it needs a new input.
		 * The inputProvider must return an AsyncWork with a null result when no more input is available and the operation should end.
		 */
		public AsyncWork<Void, ? extends Exception> startOperation(Provider<AsyncWork<Input,? extends Exception>> inputProvider, int nbInputs, Pair<Data,IO.Writable> output, Parameters params, byte priority, WorkProgress progress, long work);
	}
	
}
