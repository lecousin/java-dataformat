package net.lecousin.dataformat.core.operations;

import java.util.function.Supplier;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

/**
 * Operation that takes one or more objects in input (memory representation), and produces a data in a specific format.
 */
public interface DataFormatWriteOperation<Input, Output extends DataFormat, Parameters> extends IOperation<Parameters>, IOperation.FromObject<Input>, IOperation.ToData<Output> {

	public static interface OneToOne<Input, Output extends DataFormat, Parameters> extends DataFormatWriteOperation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncSupplier<Void,? extends Exception> execute(Input input, Pair<Data,IO.Writable> output, Parameters params, Priority priority, WorkProgress progress, long work);
	}
	
	public static interface ManyToOne<Input, Output extends DataFormat, Parameters> extends DataFormatWriteOperation<Input,Output,Parameters>, IOperation.ManyToOne {
		/**
		 * Start the operation, which will call the inputProvider each time it needs a new input.
		 * The inputProvider must return an AsyncWork with a null result when no more input is available and the operation should end.
		 */
		public AsyncSupplier<Void, ? extends Exception> startOperation(Supplier<AsyncSupplier<Input,? extends Exception>> inputProvider, int nbInputs, Pair<Data,IO.Writable> output, Parameters params, Priority priority, WorkProgress progress, long work);
	}
	
}
