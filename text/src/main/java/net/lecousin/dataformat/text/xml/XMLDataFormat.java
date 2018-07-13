package net.lecousin.dataformat.text.xml;

import net.lecousin.dataformat.text.TextDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class XMLDataFormat extends TextDataFormat {

	public static final XMLDataFormat instance = new XMLDataFormat();
	
	protected XMLDataFormat() {
	}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("XML");
	}
	
	@Override
	public String[] getMIMETypes() {
		return new String[] { "text/xml" };
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "xml" };
	}
	
	// source: https://findicons.com/icon/238224/application_xml (size 64, GNU/GPL)
	// source: https://findicons.com/icon/93956/text_xml (GNU/GPL)
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.text/images/xml_", ".png", 16, 24, 32, 48, 64, 72, 128);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
	}
}
