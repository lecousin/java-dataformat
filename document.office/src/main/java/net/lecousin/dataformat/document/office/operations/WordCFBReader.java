package net.lecousin.dataformat.document.office.operations;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.document.office.WordFile_CFB_DataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

import org.apache.poi.hwpf.HWPFDocument;

public class WordCFBReader implements DataFormatReadOperation.OneToOne<WordFile_CFB_DataFormat, HWPFDocument, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read Microsoft Word file");
	}
	
	@Override
	public WordFile_CFB_DataFormat getInputFormat() {
		return WordFile_CFB_DataFormat.instance;
	}
	
	@Override
	public Class<HWPFDocument> getOutputType() {
		return HWPFDocument.class;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return new FixedLocalizedString("DOC");
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return WordFile_CFB_DataFormat.iconProvider;
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
	public AsyncSupplier<Pair<HWPFDocument,Object>,Exception> execute(Data data, Object params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<Pair<HWPFDocument,Object>,Exception> sp = new AsyncSupplier<>();
		AsyncSupplier<CachedObject<HWPFDocument>,Exception> word = WordFile_CFB_DataFormat.cache.open(data, this, priority, progress, work);
		word.onDone(new Runnable() {
			@Override
			public void run() {
				if (word.isCancelled()) return;
				if (!word.isSuccessful()) {
					sp.unblockError(word.getError());
					return;
				}
				CachedObject<HWPFDocument> cache = word.getResult();
				@SuppressWarnings("resource")
				HWPFDocument doc = cache.get();
				if (doc == null) {
					cache.release(WordCFBReader.this);
					sp.unblockError(new Exception("Unable to read DOC file"));
					return;
				}
				sp.unblockSuccess(new Pair<>(doc,null));
			}
		});
		sp.onCancel(word::cancel);
		return sp;
	}
	
	@Override
	public void release(Data data, Pair<HWPFDocument,Object> output) {
		AsyncSupplier<CachedObject<HWPFDocument>,Exception> word = WordFile_CFB_DataFormat.cache.open(data, this, Priority.LOW, null, 0);
		word.onDone(new Runnable() {
			@Override
			public void run() {
				if (word.isSuccessful()) {
					word.getResult().release(WordCFBReader.this);
					word.getResult().release(WordCFBReader.this);
				}
			}
		});
	}
	
}
