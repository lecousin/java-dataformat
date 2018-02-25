package net.lecousin.dataformat.image.ico;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class ICODataFormat extends ICOCURFormat {

	public static ICODataFormat instance = new ICODataFormat();
	
	private ICODataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Icon"); // TODO
	}
	
	public static final String[] extensions = new String[] { "ico" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"image/ico", 
		"image/x-icon", 
		"application/ico", 
		"application/x-ico"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}

}
