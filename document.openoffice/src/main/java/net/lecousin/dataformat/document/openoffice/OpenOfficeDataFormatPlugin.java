package net.lecousin.dataformat.document.openoffice;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.document.openoffice.operations.ODT2PDF;
import net.lecousin.dataformat.document.openoffice.operations.ODTReader;

public class OpenOfficeDataFormatPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			OpenDocumentChartDataFormat.instance,
			OpenDocumentFormulaDataFormat.instance,
			OpenDocumentGraphicsDataFormat.instance,
			OpenDocumentImageDataFormat.instance,
			OpenDocumentPresentationDataFormat.instance,
			OpenDocumentSpreadsheetDataFormat.instance,
			OpenDocumentTextDataFormat.instance,
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
			new OpenOfficeDetector()
		};
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation[] {
			new ODTReader(),
			new ODT2PDF()
		};
	}
	
}
