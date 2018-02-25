package net.lecousin.dataformat.document.openoffice;

import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

public class OpenDocumentImageDataFormat extends ZipDataFormat {

	public static final OpenDocumentImageDataFormat instance = new OpenDocumentImageDataFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Image OpenDocument");
	}
	
	@Override
	public IconProvider getIconProvider() { return OpenDocumentGraphicsDataFormat.iconProvider; }
	
	public static final String[] extensions = new String[] { "odi", "oti" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.oasis.opendocument.image",
		"application/vnd.oasis.opendocument.image-template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}

	
}
