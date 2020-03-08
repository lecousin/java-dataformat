package net.lecousin.dataformat.document.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.constraints.IntegerMinimum;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PDFToImage {

	public static class ExtractPage implements Operation.OneToOne<PDDocument, BufferedImage, ExtractPage.Parameters> {
		
		public static class Parameters {
			@LocalizedName(namespace="dataformat.pdf", key="Page")
			@IntegerMinimum(1)
			public int page = 1;
			// TODO
		}
		
		@Override
		public ILocalizableString getName() {
			return new LocalizableString("dataformat.pdf","Extract page from PDF into image");
		}
		
		@Override
		public Class<PDDocument> getInputType() {
			return PDDocument.class;
		}
		@Override
		public Class<BufferedImage> getOutputType() {
			return BufferedImage.class;
		}
		
		@Override
		public ILocalizableString getOutputName() {
			return new LocalizableString("dataformat.image", "Image");
		}
		
		@Override
		public IconProvider getOutputTypeIconProvider() {
			return ImageDataFormat.iconProvider;
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
		public AsyncSupplier<Pair<BufferedImage,Object>,IOException> execute(PDDocument input, Parameters params, Priority priority, WorkProgress progress, long work) {
			Task<Pair<BufferedImage,Object>,IOException> task = Task.cpu("Extract page from PDF to image", priority, t -> {
				PDFRenderer renderer = new PDFRenderer(input);
				BufferedImage img = renderer.renderImage(params.page - 1);
				if (progress != null) progress.progress(work);
				return new Pair<>(img,null);
			});
			task.start();
			return task.getOutput();
		}
		
		@Override
		public void release(Pair<BufferedImage,Object> output) {
		}
		
	}
	
	public static class ToImages implements Operation.OneToMany<PDDocument, BufferedImage, ToImages.Parameters> {
		
		public static class Parameters {
			// TODO
		}
		
		@Override
		public ILocalizableString getName() {
			// TODO Auto-generated method stub
			return new FixedLocalizedString("PDF to Images");
		}
		
		@Override
		public ILocalizableString getVariableName() {
			return new LocalizableString("dataformat.pdf", "page");
		}
		
		@Override
		public Class<PDDocument> getInputType() {
			return PDDocument.class;
		}
		@Override
		public Class<BufferedImage> getOutputType() {
			return BufferedImage.class;
		}
		
		@Override
		public ILocalizableString getOutputName() {
			return new LocalizableString("dataformat.image", "Image");
		}
		
		@Override
		public IconProvider getOutputTypeIconProvider() {
			return ImageDataFormat.iconProvider;
		}
		
		@Override
		public Class<Parameters> getParametersClass() {
			return Parameters.class;
		}
		@Override
		public Parameters createDefaultParameters() {
			return new Parameters();
		}
		
		private static class Op {
			private PDFRenderer renderer;
			private int pageIndex = 0;
			private int nbPages;
		}
		
		@Override
		public AsyncSupplier<Object, ? extends Exception> initOperation(PDDocument input, Parameters params, Priority priority, WorkProgress progress, long work) {
			Op op = new Op();
			op.renderer = new PDFRenderer(input);
			op.nbPages = input.getNumberOfPages();
			if (progress != null) progress.progress(work);
			return new AsyncSupplier<>(op, null);
		}
		
		@Override
		public int getNbOutputs(Object operation) {
			return ((Op)operation).nbPages;
		}
		
		@Override
		public AsyncSupplier<BufferedImage, IOException> nextOutput(Object operation, Priority priority, WorkProgress progress, long work) {
			Op op = (Op)operation;
			if (op.pageIndex == op.nbPages) {
				if (progress != null) progress.progress(work);
				return new AsyncSupplier<>(null, null);
			}
			Task<BufferedImage, IOException> task = Task.cpu("Render PDF Page as Image", priority, new RenderImage(op.renderer, op.pageIndex++));
			task.start();
			if (progress != null)
				task.getOutput().onDone(new Runnable() {
					@Override
					public void run() {
						progress.progress(work);
					}
				});
			return task.getOutput();
		}
		
		public static class RenderImage implements Executable<BufferedImage, IOException> {
			public RenderImage(PDFRenderer renderer, int pageIndex) {
				this.renderer = renderer;
				this.pageIndex = pageIndex;
			}
			private PDFRenderer renderer;
			private int pageIndex;
			@Override
			public BufferedImage execute(Task<BufferedImage, IOException> taskContext) throws IOException {
				return renderer.renderImage(pageIndex);
			}
		}
		
		@Override
		public void releaseOutput(BufferedImage output) {
		}
		
		@Override
		public void releaseOperation(Object operation) {
		}
		
	}
	
}
