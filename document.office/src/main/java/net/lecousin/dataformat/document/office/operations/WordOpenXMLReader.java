package net.lecousin.dataformat.document.office.operations;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.document.office.WordOpenXMLFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

public class WordOpenXMLReader implements DataFormatReadOperation.OneToOne<WordOpenXMLFormat, XWPFDocument, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read Microsoft Word file");
	}
	
	@Override
	public WordOpenXMLFormat getInputFormat() {
		return WordOpenXMLFormat.instance;
	}
	
	@Override
	public Class<XWPFDocument> getOutputType() {
		return XWPFDocument.class;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return new FixedLocalizedString("DOCX");
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return WordOpenXMLFormat.iconProvider;
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
	public AsyncSupplier<Pair<XWPFDocument,Object>,Exception> execute(Data data, Object params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<Pair<XWPFDocument,Object>,Exception> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<XWPFDocument>,Exception> word = WordOpenXMLFormat.cache.open(data, this, priority, progress, work);
		word.onDone(new Runnable() {
			@Override
			public void run() {
				if (word.isCancelled()) return;
				if (!word.isSuccessful()) {
					sp.unblockError(word.getError());
					return;
				}
				CachedObject<XWPFDocument> cache = word.getResult();
				@SuppressWarnings("resource")
				XWPFDocument doc = cache.get();
				if (doc == null) {
					cache.release(WordOpenXMLReader.this);
					sp.unblockError(new Exception("Unable to read DOCX file"));
					return;
				}
				sp.unblockSuccess(new Pair<>(doc,null));
			}
		});
		sp.onCancel(word::cancel);
		return sp;
	}
	
	@Override
	public void release(Data data, Pair<XWPFDocument,Object> output) {
		AsyncSupplier<CachedObject<XWPFDocument>,Exception> word = WordOpenXMLFormat.cache.open(data, this, Priority.LOW, null, 0);
		word.onDone(new Runnable() {
			@Override
			public void run() {
				if (word.isSuccessful()) {
					word.getResult().release(WordOpenXMLReader.this);
					word.getResult().release(WordOpenXMLReader.this);
				}
			}
		});
	}
	
}
