package net.lecousin.dataformat.document.pdf;

import java.io.InputStream;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

public class PDFDataFormat implements DataFormat {

	public static PDFDataFormat instance = new PDFDataFormat();
	
	private PDFDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("PDF");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/pdf/pdf_", ".png", 16, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "pdf" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] { "application/pdf" };
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
	public static OpenedDataCache<PDDocument> cache = new OpenedDataCache<PDDocument>(PDDocument.class, 30*6000) {
		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<PDDocument,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			InputStream is = IOAsInputStream.get(io);
			PDDocument pdf;
			try { pdf = PDDocument.load(is); }
			catch (Exception e) {
				return new AsyncWork<>(null, e);
			}
			if (progress != null) progress.progress(work);
			return new AsyncWork<>(pdf, null);
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(PDDocument pdf) {
			try { pdf.close(); }
			catch (Throwable t) {}
		}
		
	};
	
	@Override
	public AsyncWork<DataFormatInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<CachedObject<PDDocument>,Exception> pdf = cache.open(data, this, priority, null, 0);
		Task<DataFormatInfo,Exception> task = new Task.Cpu<DataFormatInfo,Exception>("Read PDF document info", priority) {
			@SuppressWarnings("resource")
			@Override
			public DataFormatInfo run() {
				try {
					PDDocument doc = pdf.getResult().get();
					if (doc == null) return null;
					PDFInfo info = new PDFInfo();
					info.pages = doc.getNumberOfPages();
					info.encrypted = doc.isEncrypted();
					PDDocumentInformation docInfo = doc.getDocumentInformation();
					if (docInfo != null) {
						info.title = docInfo.getTitle();
						info.author = docInfo.getAuthor();
						info.subject = docInfo.getSubject();
						info.creator = docInfo.getCreator();
						info.producer = docInfo.getProducer();
					}
					return info;
				} finally {
					pdf.getResult().release(PDFDataFormat.this);
				}
			}
		};
		task.startOn(pdf, false);
		task.getOutput().onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				pdf.unblockCancel(event);
			}
		});
		return task.getOutput();
	}
	
}
