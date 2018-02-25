package net.lecousin.dataformat.core.operations;

import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

/**
 * Operation that take one or more data of a given format in input, and produces one or more objects (memory representation) in output.
 */
public interface DataFormatReadOperation<Input extends DataFormat, Output, Parameters> extends IOperation<Parameters>, IOperation.FromData<Input>, IOperation.ToObject<Output> {

	public static interface OneToOne<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncWork<Pair<Output,Object>,? extends Exception> execute(Data data, Parameters params, byte priority, WorkProgress progress, long work);
		public void release(Data data, Pair<Output,Object> output);
	}

	public static interface OneToMany<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.OneToMany {
		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation
		 */
		public AsyncWork<Object, ? extends Exception> initOperation(Data data, Parameters params, byte priority, WorkProgress progress, long work);
		/** If known return the number of outputs that will be generated, or -1 if not known. */
		public int getNbOutputs(Object operation);
		/**
		 * Produce the next output. If the operation is finished, return an AsyncWork with a null result. 
		 */
		public AsyncWork<Output, ? extends Exception> nextOutput(Object operation, byte priority, WorkProgress progress, long work);
		
		public void releaseOutput(Output output);
		public void releaseOperation(Data data, Object operation);
	}

	public static interface ManyToOne<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.ManyToOne {
		public AsyncWork<Pair<Output,Object>,? extends Exception> execute(List<Data> data, Parameters params, byte priority, WorkProgress progress, long work);
		public void release(List<Data> data, Pair<Output,Object> output);
	}

	public static interface ManyToMany<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.ManyToMany {
		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation.
		 */
		public AsyncWork<Object, ? extends Exception> initOperation(List<Data> data, Parameters params, byte priority, WorkProgress progress, long work);
		/** If known return the number of outputs that will be generated, or -1 if not known. */
		public int getNbOutputs(Object operation);
		/**
		 * Produce the next output. If the operation is finished, return an AsyncWork with a null result. 
		 */
		public AsyncWork<Output, ? extends Exception> nextOutput(Object operation, byte priority, WorkProgress progress, long work);
		
		public void releaseOutput(Output output);
		public void releaseOperation(Object operation);
	}
	
}
