package net.lecousin.dataformat.core.operations;

import net.lecousin.framework.uidescription.resources.IconProvider;

/**
 * Defines an operation on a given type of object: it takes this type in input, and produces the same type in output.
 * Example can be rescale an image.
 */
public interface TypeOperation<Type,Parameters> extends Operation.OneToOne<Type,Type,Parameters>, IOperation.FromObject<Type>, IOperation.ToObject<Type> {

	public Class<Type> getType();
	
	@Override
	public default Class<Type> getInputType() { return getType(); }
	@Override
	public default Class<Type> getOutputType() { return getType(); }
	
	public IconProvider getIconProvider();
	
}
