package net.lecousin.dataformat.document.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.mutable.Mutable;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Provider;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class ImagesToPDF implements Operation.ManyToOne<BufferedImage, PDDocument, ImagesToPDF.Parameters> {

	public static class Parameters {
		// TODO
	}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Images to PDF");
	}
	
	@Override
	public Class<BufferedImage> getInputType() {
		return BufferedImage.class;
	}
	
	@Override
	public Class<PDDocument> getOutputType() {
		return PDDocument.class;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return new FixedLocalizedString("PDF");
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return PDFDataFormat.iconProvider;
	}
	
	@Override
	public Class<Parameters> getParametersClass() {
		return Parameters.class;
	}
	
	@Override
	public Parameters createDefaultParameters() {
		return new Parameters();
	}

	@Override
	public AsyncWork<Pair<PDDocument, Object>, Exception> startOperation(
			Provider<AsyncWork<BufferedImage, ? extends Exception>> inputProvider, int nbInputs, Parameters params,
			byte priority, WorkProgress progress, long work) {
		
		AsyncWork<Pair<PDDocument, Object>, Exception> result = new AsyncWork<>();
		Mutable<PDDocument> pdf = new Mutable<>(null);
		MutableInteger nb = new MutableInteger(nbInputs);
		MutableLong remainingWork = new MutableLong(work);
		
		Runnable next = new Runnable() {
			@Override
			public void run() {
				Runnable _next = this;
				if (nb.get() == 0) {
					if (progress != null) progress.progress(remainingWork.get());
					result.unblockSuccess(new Pair<>(pdf.get(), null));
					return;
				}
				AsyncWork<BufferedImage, ? extends Exception> nextInput = inputProvider.provide();
				nextInput.listenInline(new Runnable() {
					@Override
					public void run() {
						if (nextInput.hasError()) { result.error(nextInput.getError()); return; }
						if (nextInput.isCancelled()) { result.cancel(nextInput.getCancelEvent()); return; }
						BufferedImage img = nextInput.getResult();
						if (img == null) {
							if (progress != null) progress.progress(remainingWork.get());
							result.unblockSuccess(new Pair<>(pdf.get(), null));
							return;
						}
						AddImageToPDF task = new AddImageToPDF(img, pdf.get(), priority);
						task.start();
						task.getOutput().listenInline(new Runnable() {
							@Override
							public void run() {
								if (task.hasError()) { result.error(task.getError()); return; }
								if (task.isCancelled()) { result.cancel(task.getCancelEvent()); return; }
								if (progress != null) {
									if (nb.get() > 0) {
										long step = remainingWork.get() / nb.get();
										nb.dec();
										remainingWork.sub(step);
										progress.progress(step);
									} else {
										long step = remainingWork.get() / 10;
										remainingWork.sub(step);
										progress.progress(step);
									}
								}
								_next.run();
							}
						});
					}
				});
			}
		};
		
		Task.Cpu<Void,NoException> startTask = new Task.Cpu<Void,NoException>("Initialize PDF to store images", priority) {
			@SuppressWarnings("resource")
			@Override
			public Void run() {
				pdf.set(new PDDocument(MemoryUsageSetting.setupMixed(16 * 1024 * 1024))); // max 16MB of memory
				next.run();
				return null;
			}
		};
		startTask.start();
		return result;
	}
	
	public static class AddImageToPDF extends Task.Cpu<Void, IOException> {
		public AddImageToPDF(BufferedImage image, PDDocument pdf, byte priority) {
			super("Add image to PDF", priority);
			this.image = image;
			this.pdf = pdf;
		}
		private BufferedImage image;
		private PDDocument pdf;
		@Override
		public Void run() throws IOException {
			PDPage page = new PDPage();
			pdf.addPage(page);
			PDImageXObject pdi = LosslessFactory.createFromImage(pdf, image);
			try (PDPageContentStream contentStream = new PDPageContentStream(pdf, page)) {
				contentStream.drawImage(pdi, 0, 0);
			}
			return null;
		}
	}
	
	@Override
	public void release(Pair<PDDocument,Object> output) {
		try { output.getValue1().close(); }
		catch (Throwable t) {}
	}
	
}
