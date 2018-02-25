package net.lecousin.dataformat.document.openoffice;

import net.lecousin.dataformat.archive.zip.ZipDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

public class OpenDocumentChartDataFormat extends ZipDataFormat {

	public static final OpenDocumentChartDataFormat instance = new OpenDocumentChartDataFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Chart OpenDocument");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/openoffice/chart_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "odc", "otc" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.oasis.opendocument.chart",
		"application/vnd.oasis.opendocument.chart-template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}

	
}
