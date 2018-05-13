package net.lecousin.dataformat.core.operations;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Provider;

/**
 * Base interface for operations.
 * It has a name, and can be parameterized.
 */
public interface IOperation<TParameters> {

	public ILocalizableString getName();
	
	public Class<TParameters> getParametersClass();
	public TParameters createDefaultParameters();
	
	// marker interfaces
	
	public interface OneToOne {}
	public interface OneToMany<Input, Parameters, Output> {
		/** The variable name indicates what is the kind of output.
		 * For example, an operation extracting pages from a document will return 'page' so
		 * outputs can be named page1, page2...
		 */
		public ILocalizableString getVariableName();
		
		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation.
		 * The initialization may allow to retrieve necessary information about the output that will be generated, such
		 * as the number of output.
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
	public interface ManyToOne {}
	public interface ManyToMany<Input, Parameters, Output> {
		/** The variable name indicates what is the kind of output.
		 * For example, an operation extracting pages from a document will return 'page' so
		 * outputs can be named page1, page2...
		 */
		public ILocalizableString getVariableName();

		/**
		 * Initialize the operation, and return an object (or null) that must be passed to the releaseOperation method at the end of the operation.
		 * The initialization may allow to retrieve necessary information about the output that will be generated, such
		 * as the number of output.
		 * The inputProvider is called each time it needs a new input.
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
	
	public interface FromData<Input extends DataFormat> {
		public Input getInputFormat();
	}
	
	public interface FromObject<Input> {
		
		public Class<Input> getInputType();

	}
	
	public interface ToData<Output extends DataFormat> {

		public Output getOutputFormat();
	}
	
	public interface ToObject<Output> {
		
		public Class<Output> getOutputType();
		public IconProvider getOutputTypeIconProvider();
		public ILocalizableString getOutputName();
		
	}
	
}
