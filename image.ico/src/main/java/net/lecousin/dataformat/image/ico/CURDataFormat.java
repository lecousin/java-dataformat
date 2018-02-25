package net.lecousin.dataformat.image.ico;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class CURDataFormat extends ICOCURFormat {

	public static CURDataFormat instance = new CURDataFormat();
	
	private CURDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Cursor"); // TODO
	}
	
	public static final String[] extensions = new String[] { "cur" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
}
