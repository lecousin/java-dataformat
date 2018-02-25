package net.lecousin.dataformat.document.pdf;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.Pair;

import org.apache.pdfbox.pdmodel.PDDocument;

public class PDFReader implements DataFormatReadOperation.OneToOne<PDFDataFormat, PDDocument, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read PDF");
	}
	
	@Override
	public PDFDataFormat getInputFormat() {
		return PDFDataFormat.instance;
	}
	
	@Override
	public Class<PDDocument> getOutputType() {
		return PDDocument.class;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return new FixedLocalizedString("PDF");
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return PDFDataFormat.iconProvider;
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
	public AsyncWork<Pair<PDDocument,Object>,Exception> execute(Data data, Object params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Pair<PDDocument,Object>,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<PDDocument>,Exception> pdf = PDFDataFormat.cache.open(data, this, priority, progress, work);
		pdf.listenInline(new Runnable() {
			@Override
			public void run() {
				if (pdf.isCancelled()) return;
				if (!pdf.isSuccessful()) {
					sp.unblockError(pdf.getError());
					return;
				}
				CachedObject<PDDocument> cache = pdf.getResult();
				@SuppressWarnings("resource")
				PDDocument doc = cache.get();
				if (doc == null) {
					cache.release(PDFReader.this);
					sp.unblockError(new Exception("Unable to read PDF"));
					return;
				}
				sp.unblockSuccess(new Pair<>(doc,null));
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				pdf.unblockCancel(event);
			}
		});
		return sp;
	}
	
	@Override
	public void release(Data data, Pair<PDDocument,Object> output) {
		AsyncWork<CachedObject<PDDocument>,Exception> pdf = PDFDataFormat.cache.open(data, this, Task.PRIORITY_LOW, null, 0);
		pdf.listenInline(new Runnable() {
			@Override
			public void run() {
				if (pdf.isSuccessful()) {
					pdf.getResult().release(PDFReader.this);
					pdf.getResult().release(PDFReader.this);
				}
			}
		});
	}
	
}
