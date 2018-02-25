package net.lecousin.dataformat.image.png;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.dataformat.image.png.io.PNGReader;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class PNGReaderOp implements DataFormatReadOperation.OneToOne<PNGDataFormat, BufferedImage, Object> {

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Read PNG");
	}
	
	@Override
	public PNGDataFormat getInputFormat() { return PNGDataFormat.instance; }
	@Override
	public Class<BufferedImage> getOutputType() { return BufferedImage.class; }
	
	@Override
	public ILocalizableString getOutputName() {
		return new LocalizableString("dataformat.image", "Image");
	}
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return ImageDataFormat.iconProvider;
	}
	
	@Override
	public Class<Object> getParametersClass() {
		return null;
	}
	@Override
	public Object createDefaultParameters() {
		return null;
	}
	
	@Override
	public AsyncWork<Pair<BufferedImage,Object>,Exception> execute(Data data, Object params, byte priority, WorkProgress progress, long work) {
		AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.open(priority);
		AsyncWork<Pair<BufferedImage,Object>,Exception> sp = new AsyncWork<>();
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled()) sp.unblockCancel(open.getCancelEvent());
					else sp.unblockError(open.getError());
				} else {
					if (progress != null) progress.progress(work/4);
					try {
						PNGReader.readFromSeekable((IO.Readable.Seekable&IO.KnownSize)open.getResult()).listenInline(new AsyncWorkListener<BufferedImage, Exception>() {
							@Override
							public void ready(BufferedImage result) {
								sp.unblockSuccess(new Pair<>(result, null));
							}
							@Override
							public void error(Exception error) {
								sp.error(error);
							}
							@Override
							public void cancelled(CancelException event) {
								sp.cancel(event);
							}
						});
					} catch (IOException e) {
						sp.unblockError(e);
					} finally {
						// TODO better progress
						if (progress != null) progress.progress(work-work/4);
					}
				}
			}
		});
		return sp;
	}
	
	@Override
	public void release(Data data, Pair<BufferedImage,Object> output) {
	}
	
}
