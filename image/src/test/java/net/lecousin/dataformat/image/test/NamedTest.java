package net.lecousin.dataformat.image.test;

public interface NamedTest<T> {

	public String getName();
	public void runTest(T input);
	
}
