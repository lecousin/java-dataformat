package net.lecousin.dataformat.security;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class X509CertificateDataFormat implements DataFormat {

	public static final X509CertificateDataFormat instance = new X509CertificateDataFormat();
	
	private X509CertificateDataFormat() {
	}

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("X.509 Certificate");
	}

	@Override
	public IconProvider getIconProvider() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getFileExtensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AsyncSupplier<? extends DataFormatInfo, ?> getInfo(Data data, Priority priority) {
		// TODO Auto-generated method stub
		// https://www.cryptologie.net/article/262/what-are-x509-certificates-rfc-asn1-der/
		// https://en.wikipedia.org/wiki/X.509
		// https://en.wikipedia.org/wiki/X.690#DER_encoding
		// https://www.itu.int/ITU-T/studygroups/com17/languages/X.690-0207.pdf
		return null;
	}
	
}
