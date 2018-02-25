package net.lecousin.dataformat.microsoft.ole;

import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.plugins.ExtensionPoint;

public class KnownOLEPropertySet_ExtensionPoint implements ExtensionPoint<KnownOLEPropertySet> {

	public static KnownOLEPropertySet_ExtensionPoint instance;
	public KnownOLEPropertySet_ExtensionPoint() {
		instance = this;
		known.add(new SummaryInformation());
		known.add(new DocumentSummaryInformation());
	}
	
	private ArrayList<KnownOLEPropertySet> known = new ArrayList<>();
	
	@Override
	public Class<KnownOLEPropertySet> getPluginClass() {
		return KnownOLEPropertySet.class;
	}
	
	@Override
	public void addPlugin(KnownOLEPropertySet plugin) {
		known.add(plugin);
	}
	
	public KnownOLEPropertySet get(byte[] fmtid) {
		for (KnownOLEPropertySet k : known)
			if (ArrayUtil.equals(fmtid, k.getFMTID()))
				return k;
		return null;
	}
	
	@Override
	public void allPluginsLoaded() {
	}
	
	@Override
	public Collection<KnownOLEPropertySet> getPlugins() {
		return known;
	}
}
