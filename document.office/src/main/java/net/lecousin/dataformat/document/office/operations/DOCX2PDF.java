package net.lecousin.dataformat.document.office.operations;

import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.dataformat.document.pdf.PDFDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsOutputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class DOCX2PDF implements DataFormatWriteOperation.OneToOne<XWPFDocument, PDFDataFormat, Object> {

	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("DOCX to PDF");
	}
	
	@Override
	public Class<XWPFDocument> getInputType() {
		return XWPFDocument.class;
	}
	
	@Override
	public PDFDataFormat getOutputFormat() {
		return PDFDataFormat.instance;
	}
	
	@Override
	public Class<Object> getParametersClass() {
		return null;
	}
	@Override
	public Object createDefaultParameters() {
		return null;
	}
	
	@Override
	public AsyncWork<Void, IOException> execute(XWPFDocument input, Pair<Data,IO.Writable> output, Object params, byte priority, WorkProgress progress, long work) {
		Task<Void,IOException> task = new Task.Cpu<Void,IOException>("docx to pdf", priority) {
			@Override
			public Void run() throws IOException {
				PdfOptions options = PdfOptions.create();
				output.getValue2().lockClose();
				PdfConverter.getInstance().convert(input, IOAsOutputStream.get(output.getValue2()), options);
				output.getValue2().unlockClose();
				output.getValue1().setFormat(PDFDataFormat.instance);
				if (progress != null) progress.progress(work);
				return null;
			}
		};
		task.start();
		return task.getOutput();
	}
	
}
