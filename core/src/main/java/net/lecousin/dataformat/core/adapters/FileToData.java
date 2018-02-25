package net.lecousin.dataformat.core.adapters;

import java.io.File;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.file.FileData;
import net.lecousin.framework.adapter.Adapter;

public class FileToData implements Adapter<File, Data> {

	@Override
	public Class<File> getInputType() { return File.class; }
	@Override
	public Class<Data> getOutputType() { return Data.class; }
	
	@Override
	public boolean canAdapt(File input) {
		return true;
	}
	@Override
	public Data adapt(File file) {
		return FileData.get(file);
	}
	
}
