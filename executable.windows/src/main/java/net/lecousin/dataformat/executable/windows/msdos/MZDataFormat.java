package net.lecousin.dataformat.executable.windows.msdos;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.DataWrapperDataFormat;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class MZDataFormat implements DataWrapperDataFormat {

	public static final MZDataFormat instance = new MZDataFormat();
	
	protected MZDataFormat() {}
	
	// http://www.fileformat.info/format/exe/corion-mz.htm
	// http://www.delorie.com/djgpp/doc/exe/
	// http://wiki.osdev.org/MZ
	// https://en.wikibooks.org/wiki/X86_Disassembly/Windows_Executable_Files#MS-DOS_EXE_Files
	// TODO specializations from the first link

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("MS-DOS Executable");
	}
	
	@Override
	public IconProvider getIconProvider() { return WinExeDataFormatPlugin.dosIconProvider; }
	
	@Override
	public String[] getMIMETypes() {
		return new String[] {
			"application/msdos-windows",
			"application/dos-exe"
		};
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "exe" };
	}
	
	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public AsyncWork<Data, Exception> getWrappedData(Data container, WorkProgress progress, long work) {
		SubData sd = (SubData)container.getProperty("MZSubData");
		if (sd == null) {
			Integer offset = (Integer)container.getProperty("MZDataOffset");
			if (offset == null)
				return new AsyncWork<>(null, null);
			int off = offset.intValue();
			long size = container.getSize();
			if (off == size)
				return new AsyncWork<>(null, null);
			sd = new SubData(container, off, size-off, new LocalizableString("dataformat", "Content"));
			container.setProperty("MZSubData", sd);
		}
		progress.progress(work);
		return new AsyncWork<>(sd, null);
	}
	
	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}
}
