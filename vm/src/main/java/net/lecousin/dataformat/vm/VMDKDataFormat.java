package net.lecousin.dataformat.vm;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class VMDKDataFormat implements DataFormat.DataContainerFlat {

	public static final VMDKDataFormat instance = new VMDKDataFormat();
	
	private VMDKDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Virtual Machine Disk");
	}
	
	public static final String[] mimes = new String[] { };
	public static final String[] extensions = new String[] { "vmdk" };
	
	@Override
	public String[] getMIMETypes() {
		return mimes;
	}
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	
}
