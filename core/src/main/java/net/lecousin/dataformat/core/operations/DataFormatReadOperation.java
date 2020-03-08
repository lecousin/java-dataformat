package net.lecousin.dataformat.core.operations;

import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

/**
 * Operation that take one or more data of a given format in input, and produces one or more objects (memory representation) in output.
 */
public interface DataFormatReadOperation<Input extends DataFormat, Output, Parameters> extends IOperation<Parameters>, IOperation.FromData<Input>, IOperation.ToObject<Output> {

	public static interface OneToOne<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.OneToOne {
		public AsyncSupplier<Pair<Output,Object>,? extends Exception> execute(Data data, Parameters params, Priority priority, WorkProgress progress, long work);
		public void release(Data data, Pair<Output,Object> output);
	}

	public static interface OneToMany<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.OneToMany<Data, Parameters, Output> {
	}

	public static interface ManyToOne<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.ManyToOne {
		public AsyncSupplier<Pair<Output,Object>,? extends Exception> execute(List<Data> data, Parameters params, Priority priority, WorkProgress progress, long work);
		public void release(List<Data> data, Pair<Output,Object> output);
	}

	public static interface ManyToMany<Input extends DataFormat, Output, Parameters> extends DataFormatReadOperation<Input,Output,Parameters>, IOperation.ManyToMany<Data, Parameters, Output> {
	}
	
}
