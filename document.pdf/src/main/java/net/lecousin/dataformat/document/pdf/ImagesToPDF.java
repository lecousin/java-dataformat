package net.lecousin.dataformat.document.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Supplier;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.mutable.Mutable;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

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
	public AsyncSupplier<Pair<PDDocument, Object>, Exception> startOperation(
			Supplier<AsyncSupplier<BufferedImage, ? extends Exception>> inputProvider, int nbInputs, Parameters params,
			Priority priority, WorkProgress progress, long work) {
		
		AsyncSupplier<Pair<PDDocument, Object>, Exception> result = new AsyncSupplier<>();
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
				AsyncSupplier<BufferedImage, ? extends Exception> nextInput = inputProvider.get();
				nextInput.onDone(new Runnable() {
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
						Task<Void, IOException> task = Task.cpu("Add image to PDF", priority, new AddImageToPDF(img, pdf.get()));
						task.start();
						task.getOutput().onDone(new Runnable() {
							@Override
							public void run() {
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
						}, result, e -> e);
					}
				});
			}
		};
		
		Task<Void,NoException> startTask = Task.cpu("Initialize PDF to store images", priority, t -> {
			pdf.set(new PDDocument(MemoryUsageSetting.setupMixed(16 * 1024 * 1024))); // max 16MB of memory
			next.run();
			return null;
		});
		startTask.start();
		return result;
	}
	
	public static class AddImageToPDF implements Executable<Void, IOException> {
		public AddImageToPDF(BufferedImage image, PDDocument pdf) {
			this.image = image;
			this.pdf = pdf;
		}
		private BufferedImage image;
		private PDDocument pdf;
		@Override
		public Void execute(Task<Void, IOException> taskContext) throws IOException {
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
