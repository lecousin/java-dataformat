package net.lecousin.dataformat.document.pdf;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.operations.IOperation;

public class PDFPlugin implements DataFormatPlugin {

	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] { PDFDataFormat.instance };
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new PDFDetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[0];
	}
	
	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {
			new PDFReader(),
			new PDFWriter(),
			new PDFToImage.ExtractPage(),
			new PDFToImage.ToImages(),
			new ImagesToPDF()
		};
	}
	
}
