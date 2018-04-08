package net.lecousin.dataformat.model;

public interface IModelElement {

	ModelBlock getParent();
	
	String getName();
	
	String getProperty(String name);
	void setProprty(String name, String value);
	boolean hasProperty(String name);
	
}
