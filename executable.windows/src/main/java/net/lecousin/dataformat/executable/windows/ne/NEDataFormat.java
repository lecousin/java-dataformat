package net.lecousin.dataformat.executable.windows.ne;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.dataformat.executable.windows.msdos.MZDataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class NEDataFormat extends MZDataFormat {

	// http://benoit.papillault.free.fr/c/disc2/exefmt.txt
	
	public static final NEDataFormat instance = new NEDataFormat();
	
	@Override
	public ILocalizableString getName() {
		// TODO
		return new FixedLocalizedString("Windows New Executable");
	}
	
	@Override
	public IconProvider getIconProvider() { return WinExeDataFormatPlugin.winIconProvider; }

	@Override
	public String[] getMIMETypes() {
		return new String[] { "application/exe" };
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "exe" };
	}
	
	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		// TODO
		return null;
	}
	
	@Override
	public AsyncWork<Data, Exception> getWrappedData(Data container, WorkProgress progress, long work) {
		progress.progress(work);
		return new AsyncWork<>(null, null);
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		// TODO
		return null;
	}
}
