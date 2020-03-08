package net.lecousin.dataformat.document.office.operations;

import java.io.File;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.WordToFoConverter;
import org.apache.poi.util.XMLHelper;
import org.w3c.dom.Document;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.dataformat.document.pdf.PDFDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsOutputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

public class DOC2PDF implements DataFormatWriteOperation.OneToOne<HWPFDocument, PDFDataFormat, Object> {

	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("DOC to PDF");
	}
	
	@Override
	public Class<HWPFDocument> getInputType() {
		return HWPFDocument.class;
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
	public AsyncSupplier<Void, Exception> execute(HWPFDocument input, Pair<Data,IO.Writable> output, Object params, Priority priority, WorkProgress progress, long work) {
		Task<Void,Exception> task = Task.cpu("doc to pdf", priority, t -> {
			long stepToFo = work / 3;
			long stepFop = work - stepToFo;
			
			WordToFoConverter wordToFoConverter = new WordToFoConverter(XMLHelper.getDocumentBuilderFactory().newDocumentBuilder().newDocument() );
	        wordToFoConverter.processDocument(input);
	        Document fo = wordToFoConverter.getDocument();
	        if (progress != null) progress.progress(stepToFo);
	        
	        FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
	        output.getValue2().lockClose();
	        try {
		        Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, IOAsOutputStream.get(output.getValue2()));
		        TransformerFactory factory = TransformerFactory.newInstance();
		        Transformer transformer = factory.newTransformer();
		        Source src = new DOMSource(fo);
		        Result res = new SAXResult(fop.getDefaultHandler());
		        transformer.transform(src, res);
	        } finally {
		        output.getValue2().unlockClose();
	        }
	        
			output.getValue1().setFormat(PDFDataFormat.instance);
			if (progress != null) progress.progress(stepFop);
			return null;
		});
		task.start();
		return task.getOutput();
	}
	
}
