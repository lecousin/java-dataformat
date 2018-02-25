package net.lecousin.dataformat.microsoft.dev;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.microsoft.MicrosoftAbstractDataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
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
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		return null;
	}
	
}
