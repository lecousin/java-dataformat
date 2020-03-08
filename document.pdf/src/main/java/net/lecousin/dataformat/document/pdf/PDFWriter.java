package net.lecousin.dataformat.document.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsOutputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class PDFWriter implements DataFormatWriteOperation.OneToOne<PDDocument, PDFDataFormat, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Write PDF");
	}
	
	@Override
	public Class<PDDocument> getInputType() {
		return PDDocument.class;
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
	public AsyncSupplier<Void, IOException> execute(PDDocument input, Pair<Data,IO.Writable> output, Object params, Priority priority, WorkProgress progress, long work) {
		Task<Void,IOException> task = Task.cpu("Writting PDF", priority, t -> {
			input.save(IOAsOutputStream.get(output.getValue2()));
			output.getValue1().setFormat(PDFDataFormat.instance);
			if (progress != null) progress.progress(work);
			return null;
		});
		task.start();
		return task.getOutput();
	}
	
}
