package net.lecousin.dataformat.document.office;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

public class PowerPointOpenXMLFormat extends OpenXMLFormat {

	public static final PowerPointOpenXMLFormat instance = new PowerPointOpenXMLFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Microsoft Powerpoint Presentation");
	}

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/office/powerpoint_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }

	public static final String[] extensions = new String[] { "pptx", "potx", "pptm", "potm" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.openxmlformats-officedocument.presentationml.presentation",
		"application/vnd.openxmlformats-officedocument.presentationml.template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
}
