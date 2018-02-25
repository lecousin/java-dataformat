package net.lecousin.dataformat.security;

import net.lecousin.dataformat.text.TextDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class PEMDataFormat extends TextDataFormat {

	public static final PEMDataFormat instance = new PEMDataFormat();
	
	private PEMDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("PEM Certificate");
	}
	
	// TODO mime, extensions...
	
}
