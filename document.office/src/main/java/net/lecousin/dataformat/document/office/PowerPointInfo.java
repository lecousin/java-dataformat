package net.lecousin.dataformat.document.office;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class PowerPointInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.microsoft", key="Title")
	public String title;
	@LocalizedName(namespace="dataformat.microsoft", key="Author")
	public String author;
	@LocalizedName(namespace="dataformat.microsoft", key="Last author")
	public String lastAuthor;
	@LocalizedName(namespace="dataformat.microsoft", key="Company")
	public String company;
	@LocalizedName(namespace="dataformat.microsoft", key="Slide count")
	public Integer slides;
	@LocalizedName(namespace="dataformat.microsoft", key="Application")
	public String application;
	
}
