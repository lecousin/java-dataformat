package net.lecousin.dataformat.microsoft.dev;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.microsoft.MicrosoftAbstractDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class MSFTDataFormat extends MicrosoftAbstractDataFormat {

	public static final MSFTDataFormat instance = new MSFTDataFormat();
	
	private MSFTDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Type Library");
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "tlb" };
	}
	
	@Override
	public String[] getMIMETypes() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public AsyncSupplier<? extends DataFormatInfo, ?> getInfo(Data data, Priority priority) {
		return null;
	}
	
}
