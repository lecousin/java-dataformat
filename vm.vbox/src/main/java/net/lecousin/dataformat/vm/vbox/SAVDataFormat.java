package net.lecousin.dataformat.vm.vbox;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class SAVDataFormat implements DataFormat {

	public static final SAVDataFormat instance = new SAVDataFormat();
	
	private SAVDataFormat() { /* singleton. */ }
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("VirtualBox Saved State");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.vm.vbox/images/virtualbox-vbox-", "px.png", 16, 20, 24, 32, 40, 48, 64, 72, 80, 96, 128, 256, 512);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
	}

	public static final String[] extensions = new String[] { "sav" };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}

	@Override
	public String[] getMIMETypes() {
		return new String[0];
	}
	
	@Override
	public AsyncWork<VDIDataFormatInfo, Exception> getInfo(Data data, byte priority) {
		return null;
	}

}
