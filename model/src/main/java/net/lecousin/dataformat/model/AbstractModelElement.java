package net.lecousin.dataformat.model;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractModelElement implements IModelElement {

	public AbstractModelElement(ModelBlock parent, String name) {
		this.parent = parent;
		this.name = name;
	}
	
	protected ModelBlock parent;
	protected String name;
	protected Map<String, String> properties = new HashMap<>();
	
	@Override
	public ModelBlock getParent() {
		return parent;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String getProperty(String name) {
		return properties.get(name);
	}
	
	@Override
	public boolean hasProperty(String name) {
		return properties.containsKey(name);
	}
	
	@Override
	public void setProprty(String name, String value) {
		properties.put(name, value);
	}
	
}
