package net.lecousin.dataformat.document.office;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.archive.cfb.CFBDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.util.OpenedDataCache;
import net.lecousin.dataformat.microsoft.ole.DocumentSummaryInformation;
import net.lecousin.dataformat.microsoft.ole.OLEProperty;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySet;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySetStream;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySets;
import net.lecousin.dataformat.microsoft.ole.SummaryInformation;
import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOAsInputStream;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

import org.apache.poi.hwpf.HWPFDocument;

public class WordFile_CFB_DataFormat extends CFBDataFormat {
	
	public static final WordFile_CFB_DataFormat instance = new WordFile_CFB_DataFormat();
	private WordFile_CFB_DataFormat() {}

	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Microsoft Word Document"); }
	
	@Override
	public IconProvider getIconProvider() { return WordOpenXMLFormat.iconProvider; }
	
	public static final String[] mimes = new String[] {
		"application/msword",
		"application/doc", 
		"application/vnd.msword", 
		"application/vnd.ms-word", 
		"application/winword", 
		"application/word", 
		"application/x-msw6", 
		"application/x-msword"
	};
	@Override
	public String[] getMIMETypes() { return mimes; }
	public static final String[] exts = new String[] { "doc" };
	@Override
	public String[] getFileExtensions() { return exts; }
	
	@Override
	public AsyncWork<WordInfo,Exception> getInfo(Data data, byte priority) {
		AsyncWork<WordInfo,Exception> sp = new AsyncWork<>();
		WordInfo info = new WordInfo();
		ArrayList<AsyncWork<?,?>> works = new ArrayList<>();
		JoinPoint<NoException> jp = new JoinPoint<>();
		jp.addToJoin(1);
		jp.start();
		jp.listenInline(new Runnable() {
			@Override
			public void run() {
				sp.unblockSuccess(info);
			}
		});
		CollectionListener<Data> listener = new CollectionListener<Data>() {
			@Override
			public void elementsReady(Collection<? extends Data> elements) {
				for (Data file : elements) {
					if (sp.isCancelled()) return;
					String name = file.getName();
					if ("\u0005SummaryInformation".equals(name)) {
						AsyncWork<OLEPropertySets,Exception> t = OLEPropertySetStream.readOLEPropertySets(file, priority);
						synchronized (works) { works.add(t); }
						Task<Void,Exception> t2 = new Task.Cpu<Void,Exception>("Reading SummaryInformation", priority) {
							@Override
							public Void run() throws Exception {
								try {
									if (!t.isSuccessful()) throw t.getError();
									OLEPropertySets sets = t.getResult();
									for (OLEPropertySet set : sets.propertySets) {
										if (ArrayUtil.equals(set.FMTID, SummaryInformation.FMTID)) {
											OLEProperty p = set.properties.get(new Long(SummaryInformation.TITLE));
											if (p instanceof OLEProperty.Str) info.title = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(SummaryInformation.SUBJECT));
											if (p instanceof OLEProperty.Str) info.subject = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(SummaryInformation.AUTHOR));
											if (p instanceof OLEProperty.Str) info.author = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(SummaryInformation.LAST_AUTHOR));
											if (p instanceof OLEProperty.Str) info.lastAuthor = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(SummaryInformation.KEYWORDS));
											if (p instanceof OLEProperty.Str) info.keywords = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(SummaryInformation.APPLICATION_NAME));
											if (p instanceof OLEProperty.Str) info.application = ((OLEProperty.Str)p).value; 
										}
									}
									return null;
								} finally {
									jp.joined();
								}
							}
						};
						jp.addToJoin(1);
						t2.startOn(t, true);
						synchronized (works) { works.add(t2.getOutput()); }
					} else if ("\u0005DocumentSummaryInformation".equals(name)) {
						AsyncWork<OLEPropertySets,Exception> t = OLEPropertySetStream.readOLEPropertySets(file, priority);
						synchronized (works) { works.add(t); }
						Task<Void,Exception> t2 = new Task.Cpu<Void,Exception>("Reading SummaryInformation", priority) {
							@Override
							public Void run() throws Exception {
								try {
									if (!t.isSuccessful()) throw t.getError();
									OLEPropertySets sets = t.getResult();
									for (OLEPropertySet set : sets.propertySets) {
										if (ArrayUtil.equals(set.FMTID, DocumentSummaryInformation.FMTID)) {
											OLEProperty p = set.properties.get(new Long(DocumentSummaryInformation.COMPANY));
											if (p instanceof OLEProperty.Str) info.company = ((OLEProperty.Str)p).value; 
											p = set.properties.get(new Long(DocumentSummaryInformation.CATEGORY));
											if (p instanceof OLEProperty.Str) info.category = ((OLEProperty.Str)p).value; 
										}
									}
									return null;
								} finally {
									jp.joined();
								}
							}
						};
						jp.addToJoin(1);
						t2.startOn(t, true);
						synchronized (works) { works.add(t2.getOutput()); }
					}

				}
				CFBDataFormat.instance.unlistenSubData(data, this);
				jp.joined();
			}

			@Override
			public void elementsAdded(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsRemoved(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsChanged(Collection<? extends Data> elements) {
			}

			@Override
			public void error(Throwable error) {
				jp.joined();
			}
		};
		CFBDataFormat.instance.listenSubData(data, listener);
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				synchronized (works) {
					for (AsyncWork<?,?> w : works)
						w.unblockCancel(event);
				}
			}
		});
		return sp;
	}
	
	public static OpenedDataCache<HWPFDocument> cache = new OpenedDataCache<HWPFDocument>(HWPFDocument.class, 30*6000) {

		@SuppressWarnings("resource")
		@Override
		protected AsyncWork<HWPFDocument,Exception> open(Data data, IO.Readable io, WorkProgress progress, long work) {
			InputStream is = IOAsInputStream.get(io);
			try { return new AsyncWork<>(new HWPFDocument(is), null); }
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
		protected void close(HWPFDocument word) {
			try { word.close(); }
			catch (Throwable t) {}
		}
		
	};
	
}
