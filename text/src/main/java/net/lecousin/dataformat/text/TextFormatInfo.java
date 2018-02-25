package net.lecousin.dataformat.text;

import java.nio.charset.Charset;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.FixedName;

public class TextFormatInfo implements DataFormatInfo {

	// TODO @LocalizedName
	@FixedName("Encoding")
	public Charset encoding;
	
}
