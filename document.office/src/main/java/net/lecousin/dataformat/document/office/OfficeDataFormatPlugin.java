package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.document.office.operations.DOC2PDF;
import net.lecousin.dataformat.document.office.operations.DOCX2PDF;
import net.lecousin.dataformat.document.office.operations.WordCFBReader;
import net.lecousin.dataformat.document.office.operations.WordOpenXMLReader;

public class OfficeDataFormatPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			ExcelOpenXMLFormat.instance,
			WordOpenXMLFormat.instance,
			PowerPointOpenXMLFormat.instance,
			ExcelFile_CFB_BIFF5_7_DataFormat.instance,
			ExcelFile_CFB_BIFF8_DataFormat.instance,
			PowerPointFile_CFB_DataFormat.instance,
			VisioFile_CFB_DataFormat.instance,
			WordFile_CFB_DataFormat.instance,
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
			new OfficeOpenXMLDetector(),
			new CFBSpecializationDetector(),
		};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
			new WordOpenXMLReader(),
			new DOCX2PDF(),
			new WordCFBReader(),
			new DOC2PDF()
		};
	}
	
}
