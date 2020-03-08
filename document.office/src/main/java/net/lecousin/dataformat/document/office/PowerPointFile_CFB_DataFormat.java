package net.lecousin.dataformat.document.office;

import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.archive.cfb.CFBDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.microsoft.ole.DocumentSummaryInformation;
import net.lecousin.dataformat.microsoft.ole.OLEProperty;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySet;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySetStream;
import net.lecousin.dataformat.microsoft.ole.OLEPropertySets;
import net.lecousin.dataformat.microsoft.ole.SummaryInformation;
import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.JoinPoint;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class PowerPointFile_CFB_DataFormat extends CFBDataFormat {
	
	public static final PowerPointFile_CFB_DataFormat instance = new PowerPointFile_CFB_DataFormat();
	private PowerPointFile_CFB_DataFormat() {}

	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Microsoft PowerPoint Presentation"); }
	
	@Override
	public IconProvider getIconProvider() { return PowerPointOpenXMLFormat.iconProvider; }
	
	public static final String[] mimes = new String[] {
		"application/vnd.ms-powerpoint", 
		"application/mspowerpoint", 
		"application/ms-powerpoint", 
		"application/mspowerpnt", 
		"application/vnd-mspowerpoint",
		"application/powerpoint", 
		"application/x-powerpoint"
	};
	@Override
	public String[] getMIMETypes() { return mimes; }
	public static final String[] exts = new String[] { "ppt" };
	@Override
	public String[] getFileExtensions() { return exts; }
	
	@Override
	public AsyncSupplier<PowerPointInfo,Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<PowerPointInfo,Exception> sp = new AsyncSupplier<>();
		PowerPointInfo info = new PowerPointInfo();
		ArrayList<AsyncSupplier<?,?>> works = new ArrayList<>();
		JoinPoint<NoException> jp = new JoinPoint<>();
		jp.addToJoin(1);
		jp.start();
		jp.onDone(new Runnable() {
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
					String name = file.getName().appLocalizationSync();
					if ("\u0005SummaryInformation".equals(name)) {
						AsyncSupplier<OLEPropertySets,Exception> t = OLEPropertySetStream.readOLEPropertySets(file, priority);
						synchronized (works) { works.add(t); }
						Task<Void,Exception> t2 = Task.cpu("Reading SummaryInformation", priority, task -> {
							try {
								if (t.hasError()) throw t.getError();
								OLEPropertySets sets = t.getResult();
								for (OLEPropertySet set : sets.propertySets) {
									if (ArrayUtil.equals(set.FMTID, SummaryInformation.FMTID)) {
										OLEProperty p = set.properties.get(new Long(SummaryInformation.TITLE));
										if (p instanceof OLEProperty.Str) info.title = ((OLEProperty.Str)p).value; 
										p = set.properties.get(new Long(SummaryInformation.AUTHOR));
										if (p instanceof OLEProperty.Str) info.author = ((OLEProperty.Str)p).value; 
										p = set.properties.get(new Long(SummaryInformation.LAST_AUTHOR));
										if (p instanceof OLEProperty.Str) info.lastAuthor = ((OLEProperty.Str)p).value; 
										p = set.properties.get(new Long(SummaryInformation.APPLICATION_NAME));
										if (p instanceof OLEProperty.Str) info.application = ((OLEProperty.Str)p).value; 
									}
								}
								return null;
							} finally {
								jp.joined();
							}
						});
						jp.addToJoin(1);
						t2.startOn(t, true);
						synchronized (works) { works.add(t2.getOutput()); }
					} else if ("\u0005DocumentSummaryInformation".equals(name)) {
						AsyncSupplier<OLEPropertySets,Exception> t = OLEPropertySetStream.readOLEPropertySets(file, priority);
						synchronized (works) { works.add(t); }
						Task<Void,Exception> t2 = Task.cpu("Reading SummaryInformation", priority, task -> {
							try {
								if (t.hasError()) throw t.getError();
								OLEPropertySets sets = t.getResult();
								for (OLEPropertySet set : sets.propertySets) {
									if (ArrayUtil.equals(set.FMTID, DocumentSummaryInformation.FMTID)) {
										OLEProperty p = set.properties.get(new Long(DocumentSummaryInformation.COMPANY));
										if (p instanceof OLEProperty.Str) info.company = ((OLEProperty.Str)p).value; 
										p = set.properties.get(new Long(DocumentSummaryInformation.SLIDE_COUNT));
										if (p instanceof OLEProperty.Int) info.slides = new Integer(((OLEProperty.Int)p).value); 
									}
								}
								return null;
							} finally {
								jp.joined();
							}
						});
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
		sp.onCancel(event -> {
			synchronized (works) {
				for (AsyncSupplier<?,?> w : works)
					w.unblockCancel(event);
			}
		});
		return sp;
	}
}
