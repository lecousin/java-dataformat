package net.lecousin.dataformat.document.pdf;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.LocalizedName;

public class PDFInfo implements DataFormatInfo {

	@LocalizedName(namespace="dataformat.pdf",key="title")
	public String title = null;
	
	@LocalizedName(namespace="dataformat.pdf",key="subject")
	public String subject = null;
	
	@LocalizedName(namespace="dataformat.pdf",key="author")
	public String author = null;
	
	@LocalizedName(namespace="dataformat.pdf",key="pages")
	public int pages = 0;
	
	@LocalizedName(namespace="dataformat.pdf",key="creator")
	public String creator = null;
	
	@LocalizedName(namespace="dataformat.pdf",key="producer")
	public String producer = null;
	
	@LocalizedName(namespace="dataformat.pdf",key="encrypted")
	public boolean encrypted = false;
	
}
