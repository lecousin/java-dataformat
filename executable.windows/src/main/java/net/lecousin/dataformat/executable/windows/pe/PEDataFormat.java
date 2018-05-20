package net.lecousin.dataformat.executable.windows.pe;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.dataformat.executable.windows.coff.COFFDataFormat;
import net.lecousin.dataformat.executable.windows.msdos.MZDataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class PEDataFormat extends MZDataFormat {

	public static final PEDataFormat instance = new PEDataFormat();
	
	@Override
	public ILocalizableString getName() {
		// TODO
		return new FixedLocalizedString("Windows Executable");
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
		SubData coff = (SubData)container.getProperty("COFF");
		if (coff == null) {
			long peOffset = ((Long)container.getProperty("PEOffset")).longValue();
			coff = new SubData(container, peOffset+4, container.getSize()-peOffset-4, new FixedLocalizedString("COFF"));
			coff.setFormat(COFFDataFormat.instance);
			container.setProperty("COFF", coff);
			coff.setProperty("COFFOffset", new Long(peOffset+4));
		}
		progress.progress(work);
		return new AsyncWork<>(coff, null);
	}
}
