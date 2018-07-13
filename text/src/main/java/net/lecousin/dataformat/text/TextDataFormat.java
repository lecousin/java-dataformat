package net.lecousin.dataformat.text;

import java.nio.charset.Charset;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class TextDataFormat implements DataFormat {

	public static final TextDataFormat instance = new TextDataFormat();
	
	public static final String PROPERTY_CHARSET = "charset";
	
	protected TextDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.text", "Text");
	}
	
	@Override
	public String[] getMIMETypes() {
		return new String[] { "text/plain" };
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "txt" };
	}
	
	// source: https://www.iconfinder.com/icons/9088/document_enriched_list_paper_text_icon (Creative Commons (Attribution-NonCommercial-NoDerivs 2.5 Generic))
	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.text/images/text_", ".png", 16, 32, 48, 64, 128);
	
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	@Override
	public AsyncWork<TextFormatInfo, ?> getInfo(Data data, byte priority) {
		TextFormatInfo info = new TextFormatInfo();
		info.encoding = (Charset)data.getProperty(PROPERTY_CHARSET);
		return new AsyncWork<>(info, null);
	}
	
}
