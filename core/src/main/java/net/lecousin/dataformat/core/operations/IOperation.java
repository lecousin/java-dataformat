package net.lecousin.dataformat.core.operations;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

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
	public interface OneToMany {
		public ILocalizableString getVariableName();
	}
	public interface ManyToOne {}
	public interface ManyToMany {
		public ILocalizableString getVariableName();
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
