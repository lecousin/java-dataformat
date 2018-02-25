package net.lecousin.dataformat.microsoft.ole;

import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.plugins.Plugin;

public interface KnownOLEPropertySet extends Plugin {

	public byte[] getFMTID();
	public ILocalizableString getName(long prop_id) throws IgnoreIt;
	
	@SuppressWarnings("serial")
	public static class IgnoreIt extends Exception {	
	}
	
}
