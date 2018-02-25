package net.lecousin.dataformat.document.openoffice;

import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class OpenDocumentFormulaDataFormat extends ZipDataFormat {

	public static final OpenDocumentFormulaDataFormat instance = new OpenDocumentFormulaDataFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Formula OpenDocument");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/openoffice/formula_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "odf", "otf" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.oasis.opendocument.formula",
		"application/vnd.oasis.opendocument.formula-template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}

	
}
