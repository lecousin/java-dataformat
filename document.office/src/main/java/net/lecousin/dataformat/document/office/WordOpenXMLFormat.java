package net.lecousin.dataformat.document.office;

import java.io.InputStream;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

public class WordOpenXMLFormat extends OpenXMLFormat {

	public static final WordOpenXMLFormat instance = new WordOpenXMLFormat();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Microsoft Word Document");
	}

	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/office/word_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = new String[] { "docx", "dotx", "docm", "dotm" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	public static final String[] mime = new String[] {
		"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		"application/vnd.openxmlformats-officedocument.wordprocessingml.template"
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	
	public static OpenedDataCache<XWPFDocument> cache = new OpenedDataCache<XWPFDocument>(XWPFDocument.class, 30*6000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<XWPFDocument,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			InputStream is = IOAsInputStream.get(io);
			try { return new AsyncWork<>(new XWPFDocument(is), null); }
			catch (Exception e) { return new AsyncWork<>(null, e); }
			finally {
				if (progress != null) progress.progress(work);
			}
		}

		@Override
		protected boolean closeIOafterOpen() {
			return true;
		}

		@Override
		protected void close(XWPFDocument word) {
			try { word.close(); }
			catch (Throwable t) {}
		}
		
	};
	
}
