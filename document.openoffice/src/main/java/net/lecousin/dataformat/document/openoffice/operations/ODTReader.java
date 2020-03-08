package net.lecousin.dataformat.document.openoffice.operations;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.document.openoffice.OpenDocumentTextDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

import org.odftoolkit.odfdom.doc.OdfTextDocument;

public class ODTReader implements DataFormatReadOperation.OneToOne<OpenDocumentTextDataFormat, OdfTextDocument, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read OpenOffice Text Document");
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
	public OpenDocumentTextDataFormat getInputFormat() {
		return OpenDocumentTextDataFormat.instance;
	}

	@Override
	public Class<OdfTextDocument> getOutputType() {
		return OdfTextDocument.class;
	}

	@Override
	public IconProvider getOutputTypeIconProvider() {
		return OpenDocumentTextDataFormat.iconProvider;
	}

	@Override
	public ILocalizableString getOutputName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("ODT");
	}

	@Override
	public AsyncSupplier<Pair<OdfTextDocument, Object>, Exception> execute(Data data, Object params, Priority priority, WorkProgress progress, long work) {
		AsyncSupplier<? extends IO.Readable, IOException> open = data.openReadOnly(priority);
		AsyncSupplier<Pair<OdfTextDocument, Object>, Exception> result = new AsyncSupplier<>();
		open.thenStart("Read ODT", priority, (Task<Void, NoException> t) -> {
			if (open.hasError()) { result.error(open.getError()); return null; }
			if (open.isCancelled()) { result.cancel(open.getCancelEvent()); return null; }
			try {
				OdfTextDocument doc = OdfTextDocument.loadDocument(IOAsInputStream.get(open.getResult(), true));
				result.unblockSuccess(new Pair<>(doc, null));
			} catch (Exception e) {
				result.error(e);
			} finally {
				if (progress != null) progress.progress(work); // TODO
				open.getResult().closeAsync();
			}
			return null;
		}, true);
		return result;
	}

	@Override
	public void release(Data data, Pair<OdfTextDocument, Object> output) {
		output.getValue1().close();
	}

	
	
}
