package net.lecousin.dataformat.document.office;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class ExcelFile_CFB_BIFF5_7_DataFormat extends ExcelFile_CFB {

	public static final ExcelFile_CFB_BIFF5_7_DataFormat instance = new ExcelFile_CFB_BIFF5_7_DataFormat();
	private ExcelFile_CFB_BIFF5_7_DataFormat() {}
	
	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Microsoft Excel Workbook (5.0/95)"); }
	
}
