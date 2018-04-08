package net.lecousin.dataformat.model;

import net.lecousin.framework.event.Event;
import net.lecousin.framework.io.IO;

public abstract class ModelVariable<T> extends AbstractModelElement {

	public ModelVariable(ModelBlock parent, String name) {
		super(parent, name);
	}
	
	public Event<ModelVariable<T>> changed = new Event<>();
	
	public abstract T getValue(IO.Readable.Seekable io) throws Exception;
	
}
