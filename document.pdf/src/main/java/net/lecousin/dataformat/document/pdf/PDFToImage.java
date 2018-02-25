package net.lecousin.dataformat.document.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui_description.annotations.constraints.IntegerMinimum;
import net.lecousin.framework.ui_description.annotations.name.LocalizedName;
import net.lecousin.framework.ui_description.resources.IconProvider;
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
		public AsyncWork<Pair<BufferedImage,Object>,IOException> execute(PDDocument input, Parameters params, byte priority, WorkProgress progress, long work) {
			Task<Pair<BufferedImage,Object>,IOException> task = new Task.Cpu<Pair<BufferedImage,Object>,IOException>("Extract page from PDF to image", priority) {
				@Override
				public Pair<BufferedImage,Object> run() throws IOException {
					PDFRenderer renderer = new PDFRenderer(input);
					BufferedImage img = renderer.renderImage(params.page - 1);
					if (progress != null) progress.progress(work);
					return new Pair<>(img,null);
				}
			};
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
		public AsyncWork<Object, ? extends Exception> initOperation(PDDocument input, Parameters params, byte priority, WorkProgress progress, long work) {
			Op op = new Op();
			op.renderer = new PDFRenderer(input);
			op.nbPages = input.getNumberOfPages();
			if (progress != null) progress.progress(work);
			return new AsyncWork<>(op, null);
		}
		
		@Override
		public int getNbOutputs(Object operation) {
			return ((Op)operation).nbPages;
		}
		
		@Override
		public AsyncWork<BufferedImage, IOException> nextOutput(Object operation, byte priority, WorkProgress progress, long work) {
			Op op = (Op)operation;
			if (op.pageIndex == op.nbPages) {
				if (progress != null) progress.progress(work);
				return new AsyncWork<>(null, null);
			}
			RenderImage task = new RenderImage(op.renderer, op.pageIndex++, priority);
			task.start();
			if (progress != null)
				task.getOutput().listenInline(new Runnable() {
					@Override
					public void run() {
						progress.progress(work);
					}
				});
			return task.getOutput();
		}
		
		public static class RenderImage extends Task.Cpu<BufferedImage, IOException> {
			public RenderImage(PDFRenderer renderer, int pageIndex, byte priority) {
				super("Render PDF Page as Image", priority);
				this.renderer = renderer;
				this.pageIndex = pageIndex;
			}
			private PDFRenderer renderer;
			private int pageIndex;
			@Override
			public BufferedImage run() throws IOException {
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
