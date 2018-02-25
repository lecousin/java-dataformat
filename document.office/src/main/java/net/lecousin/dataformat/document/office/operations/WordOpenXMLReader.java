package net.lecousin.dataformat.document.office.operations;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.document.office.WordOpenXMLFormat;
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
	public AsyncWork<Pair<XWPFDocument,Object>,Exception> execute(Data data, Object params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Pair<XWPFDocument,Object>,Exception> sp = new AsyncWork<>();
		AsyncWork<CachedObject<XWPFDocument>,Exception> word = WordOpenXMLFormat.cache.open(data, this, priority, progress, work);
		word.listenInline(new Runnable() {
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
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				word.unblockCancel(event);
			}
		});
		return sp;
	}
	
	@Override
	public void release(Data data, Pair<XWPFDocument,Object> output) {
		AsyncWork<CachedObject<XWPFDocument>,Exception> word = WordOpenXMLFormat.cache.open(data, this, Task.PRIORITY_LOW, null, 0);
		word.listenInline(new Runnable() {
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
