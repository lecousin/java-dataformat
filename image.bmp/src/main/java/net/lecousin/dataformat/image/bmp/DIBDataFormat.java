package net.lecousin.dataformat.image.bmp;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DIBDataFormat extends ImageDataFormat {

	public static final Log logger = LogFactory.getLog("DIB");
	
	public static final DIBDataFormat instance = new DIBDataFormat();
	private DIBDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("DIB");
	}

	public static final String[] mime = new String[] {};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	public static final String[] extensions = new String[] { "dib" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	@Override
	public AsyncSupplier<DIBInfo, Exception> getInfo(Data data, Priority priority) {
		return DIBInfo.load(data, priority, 0);
	}
	
}
