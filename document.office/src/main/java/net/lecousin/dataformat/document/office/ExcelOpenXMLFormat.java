package net.lecousin.dataformat.document.office;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

public class ExcelOpenXMLFormat extends OpenXMLFormat {

	public static final ExcelOpenXMLFormat instance = new ExcelOpenXMLFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Microsoft Excel Workbook");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/office/excel_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "xlsx", "xlsm", "xltx", "xltm" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
		"application/vnd.openxmlformats-officedocument.spreadsheetml.template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
}
