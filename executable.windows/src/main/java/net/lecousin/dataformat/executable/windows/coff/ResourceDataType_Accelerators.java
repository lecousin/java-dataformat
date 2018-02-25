package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;

public class ResourceDataType_Accelerators implements DataFormat {

	public static final ResourceDataType_Accelerators instance = new ResourceDataType_Accelerators();
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Accelerators");
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
