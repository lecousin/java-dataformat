package net.lecousin.dataformat.microsoft.ole;

import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.microsoft.GUIDRenderer;
import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.properties.PropertiesContainer;
import net.lecousin.framework.uidescription.annotations.name.FixedName;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.annotations.render.Render;
import net.lecousin.framework.util.Pair;

public class OLEPropertySets implements DataFormatInfo, PropertiesContainer {

	@LocalizedName(namespace="dataformat.microsoft", key="System identifier")
	public long systemIdentifier;
	
	@FixedName("CLSID")
	@Render(GUIDRenderer.class)
	public byte[] CLSID;
	
	public ArrayList<OLEPropertySet> propertySets = new ArrayList<>(2);
	
	@Override
	public Collection<Pair<ILocalizableString, Object>> getProperties() {
		LinkedArrayList<Pair<ILocalizableString,Object>> list = new LinkedArrayList<>(10);
		for (OLEPropertySet set : propertySets)
			list.addAll(set.getProperties());
		return list;
	}
}
