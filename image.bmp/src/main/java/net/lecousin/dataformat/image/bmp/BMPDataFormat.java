package net.lecousin.dataformat.image.bmp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class BMPDataFormat extends ImageDataFormat {

	public static final Log logger = LogFactory.getLog("BMP");
	
	public static final BMPDataFormat instance = new BMPDataFormat();
	private BMPDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("BMP");
	}

	public static final String[] mime = new String[] {
		 "image/bmp", 
		 "image/x-bmp", 
		 "image/x-bitmap", 
		 "image/x-xbitmap", 
		 "image/x-win-bitmap", 
		 "image/x-windows-bmp", 
		 "image/ms-bmp", 
		 "image/x-ms-bmp", 
		 "application/bmp", 
		 "application/x-bmp", 
		 "application/x-win-bitmap" 
	};
	@Override
	public String[] getMIMETypes() {
		return mime;
	}
	public static final String[] extensions = new String[] { "bmp" };
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	@Override
	public AsyncWork<DIBInfo, Exception> getInfo(Data data, byte priority) {
		return DIBInfo.load(data, priority, 14);
	}
	
}
