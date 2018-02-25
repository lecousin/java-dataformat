package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class ResourceDataType_Menu implements DataFormat {

	public static final ResourceDataType_Menu instance = new ResourceDataType_Menu();

	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Menu");
	}

	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		return null;
	}

	@Override
	public IconProvider getIconProvider() {
		return null;
	}

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}
	
}
