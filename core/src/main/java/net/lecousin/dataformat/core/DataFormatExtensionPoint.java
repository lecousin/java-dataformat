package net.lecousin.dataformat.core;

import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.core.operations.DataOperationsRegistry;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.framework.plugins.ExtensionPoint;

public class DataFormatExtensionPoint implements ExtensionPoint<DataFormatPlugin> {
	
	private ArrayList<DataFormatPlugin> plugins = new ArrayList<>();
	
	@Override
	public Class<DataFormatPlugin> getPluginClass() {
		return DataFormatPlugin.class;
	}

	@Override
	public void addPlugin(DataFormatPlugin plugin) {
		plugins.add(plugin);
		for (DataFormat format : plugin.getFormats())
			DataFormatRegistry.registerFormat(format);
		
		for (DataFormatDetector detector : plugin.getDetectors())
			DataFormatRegistry.registerDetector(detector);
		
		for (DataFormatSpecializationDetector detector : plugin.getSpecializationDetectors())
			DataFormatRegistry.registerSpecializationDetector(detector);
		
		for (IOperation<?> op : plugin.getOperations())
			DataOperationsRegistry.register(op);
	}
	
	@Override
	public void allPluginsLoaded() {
	}
	
	@Override
	public Collection<DataFormatPlugin> getPlugins() {
		return plugins;
	}
}
