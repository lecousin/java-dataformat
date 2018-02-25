package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.archive.cfb.CFBDataFormat;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class VisioFile_CFB_DataFormat extends CFBDataFormat {
	
	public static final VisioFile_CFB_DataFormat instance = new VisioFile_CFB_DataFormat();
	private VisioFile_CFB_DataFormat() {}

	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Microsoft Visio Document"); }
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/document/office/visio_", ".png", 16, 24, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] mimes = new String[] {
		// TODO
	};
	@Override
	public String[] getMIMETypes() { return mimes; }
	public static final String[] exts = new String[] { "vsd" };
	@Override
	public String[] getFileExtensions() { return exts; }
	
}
