package net.lecousin.dataformat.core.operations;

import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

/**
 * Operation that take one or more data in input, and produces a data in a specific format as output.
 * An example is the creation of an archive: we can take several data of any type, and produce an archive in a specific format such as zip.
 * Another example could be the creation of a multimedia file: we can take some video and audio streams, and produce a multimedia data.
 */
public interface DataToDataOperation<Output extends DataFormat, TParameters> extends IOperation<TParameters>, IOperation.ToData<Output> {

	/** returns null or empty list if any type of data is accepted. */
	public List<Class<? extends DataFormat>> getAcceptedInputs();
	
	public static interface OneToOne<Output extends DataFormat, TParameters> extends DataToDataOperation<Output, TParameters>, IOperation.OneToOne {
		public AsyncSupplier<Void,? extends Exception> execute(Data input, Pair<Data,IO.Writable> output, TParameters params, Priority priority, WorkProgress progress, long work);
	}
	
	public static interface ManyToOne<Output extends DataFormat, TParameters> extends DataToDataOperation<Output, TParameters>, IOperation.ManyToOne {
		public AsyncSupplier<Void,? extends Exception> execute(List<Data> input, Pair<Data,IO.Writable> output, TParameters params, Priority priority, WorkProgress progress, long work);
	}
	
}
