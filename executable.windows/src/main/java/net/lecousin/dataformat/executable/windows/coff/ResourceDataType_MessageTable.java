package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class ResourceDataType_MessageTable implements DataFormat {

	public static final ResourceDataType_MessageTable instance = new ResourceDataType_MessageTable();

	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Message Table");
	}

	@Override
	public AsyncSupplier<? extends DataFormatInfo, ?> getInfo(Data data, Priority priority) {
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
