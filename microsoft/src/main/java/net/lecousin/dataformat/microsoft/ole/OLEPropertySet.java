package net.lecousin.dataformat.microsoft.ole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.microsoft.ole.KnownOLEPropertySet.IgnoreIt;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.properties.PropertiesContainer;
import net.lecousin.framework.properties.Property;
import net.lecousin.framework.util.GUIDUtil;
import net.lecousin.framework.util.Pair;

public class OLEPropertySet implements DataFormatInfo, PropertiesContainer {
	
	@Property(name="name",value="FMTID")
	public byte[] FMTID;
	
	public HashMap<Long,OLEProperty> properties = new HashMap<>();
	public HashMap<Long,String> propertiesNames = null;
	
	@Override
	public Collection<Pair<ILocalizableString, Object>> getProperties() {
		ArrayList<Pair<ILocalizableString,Object>> props = new ArrayList<>(properties.size());
		KnownOLEPropertySet known = KnownOLEPropertySet_ExtensionPoint.instance.get(FMTID);
		if (known == null) props.add(new Pair<>(new FixedLocalizedString("FMTID"), GUIDUtil.toString(FMTID)));
		for (Map.Entry<Long,OLEProperty> e : properties.entrySet()) {
			if (e.getKey().longValue() == 1) continue; // codepage
			ILocalizableString name = null;
			if (known != null) 
				try { name = known.getName(e.getKey().longValue()); }
				catch (IgnoreIt ex) { continue; } 
			if (name == null && propertiesNames != null) {
				String s = propertiesNames.get(e.getKey());
				if (s != null) name = new FixedLocalizedString(s);
			}
			if (name == null) name = new FixedLocalizedString(Long.toString(e.getKey().longValue()));
			props.add(new Pair<>(name, e.getValue()));
		}
		return props;
	}

}
