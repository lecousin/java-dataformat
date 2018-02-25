package net.lecousin.dataformat.executable.windows.pe;

import java.util.Collections;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.dataformat.executable.windows.coff.COFFDataFormat;
import net.lecousin.dataformat.executable.windows.msdos.MZDataFormat;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
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
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		SubData coff = (SubData)data.getProperty("COFF");
		if (coff == null) {
			long peOffset = ((Long)data.getProperty("PEOffset")).longValue();
			coff = new SubData(data, peOffset+4, data.getSize()-peOffset-4, "COFF");
			coff.setFormat(COFFDataFormat.instance);
			data.setProperty("COFF", coff);
			coff.setProperty("COFFOffset", new Long(peOffset+4));
		}
		list.newElements(Collections.singletonList(coff));
		list.done();
	}
}
