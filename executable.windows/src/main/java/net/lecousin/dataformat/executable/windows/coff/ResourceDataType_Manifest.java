package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.SimpleBufferedReadable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.UnprotectedStringBuffer;
import net.lecousin.framework.xml.XMLStreamEvents;
import net.lecousin.framework.xml.XMLStreamReader;

public class ResourceDataType_Manifest implements DataFormat {

	public static final ResourceDataType_Manifest instance = new ResourceDataType_Manifest();
	
	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Side-by-side Assembly Manifest"); }
	
	@Override
	public AsyncWork<ManifestDataInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<ManifestDataInfo, Exception> result = new AsyncWork<ManifestDataInfo, Exception>();
		AsyncWork<? extends IO.Readable.Seekable,Exception> open = data.open(priority);
		open.listenInline(new Runnable() {
			@SuppressWarnings("resource")
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled())
						result.unblockCancel(open.getCancelEvent());
					else
						result.unblockError(open.getError());
					return;
				}
				IO.Readable io = open.getResult();
				IO.Readable.Buffered bio;
				if (io instanceof IO.Readable.Buffered)
					bio = (IO.Readable.Buffered)io;
				else
					bio = new SimpleBufferedReadable(io, 4096);
				AsyncWork<XMLStreamReader, Exception> xml = XMLStreamReader.start(bio, 1024);
				xml.listenAsync(new Task.Cpu<Void, NoException>("Read COFF manifest", priority) {
					@Override
					public Void run() {
						if (xml.hasError()) result.unblockError(xml.getError());
						else {
							ManifestDataInfo manifest = new ManifestDataInfo();
							XMLStreamReader xs = xml.getResult();
							try {
								while (!XMLStreamEvents.Event.Type.START_ELEMENT.equals(xs.event.type)) xs.next();
								if (!xs.searchElement("assembly")) {
									result.unblockError(new Exception("Invalid manifest"));
								} else {
									if (xs.nextInnerElement(xs.event.context.getFirst(), "assemblyIdentity")) {
										UnprotectedStringBuffer s;
										s = xs.getAttributeValueByLocalName("name");
										manifest.assembly_name = s == null ? null : s.asString();
										s = xs.getAttributeValueByLocalName("version");
										manifest.assembly_version = s == null ? null : s.asString();
									}
									result.unblockSuccess(manifest);
								}
							} catch (Exception e) {
								result.unblockError(e);
							}
						}
						io.closeAsync();
						return null;
					}
				}, true);
			}
		});
		return result;
	}

	@Override
	public IconProvider getIconProvider() {
		return null;
	}

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}
	
}
