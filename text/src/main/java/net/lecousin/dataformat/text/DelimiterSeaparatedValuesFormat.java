package net.lecousin.dataformat.text;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class DelimiterSeaparatedValuesFormat extends TextDataFormat {

	public static final DelimiterSeaparatedValuesFormat instance = new DelimiterSeaparatedValuesFormat();
	
	private DelimiterSeaparatedValuesFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Delimiter Separated Values");
	}
	
}
