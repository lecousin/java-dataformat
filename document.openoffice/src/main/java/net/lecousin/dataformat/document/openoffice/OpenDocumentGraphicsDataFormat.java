package net.lecousin.dataformat.document.openoffice;

import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class OpenDocumentGraphicsDataFormat extends ZipDataFormat {

	public static final OpenDocumentGraphicsDataFormat instance = new OpenDocumentGraphicsDataFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Drawing OpenDocument");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/openoffice/graphics_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "odg", "otg" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.oasis.opendocument.graphics",
		"application/vnd.oasis.opendocument.graphics-template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}

	
}
