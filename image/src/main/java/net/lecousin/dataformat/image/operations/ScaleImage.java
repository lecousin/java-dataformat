package net.lecousin.dataformat.image.operations;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

import net.lecousin.dataformat.core.operations.TypeOperation;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.annotations.constraints.DecimalGreaterThan;
import net.lecousin.framework.uidescription.annotations.constraints.IntegerMinimum;
import net.lecousin.framework.uidescription.annotations.constraints.OnlyIfEnum;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class ScaleImage implements TypeOperation<BufferedImage, ScaleImage.Parameters> {
	
	public static class Parameters {

		public static enum Type {
			@LocalizedName(namespace="dataformat.image",key="Scale factor")
			ScaleFactor,
			@LocalizedName(namespace="dataformat.image",key="Fixed size")
			Fixed
		}
		
		public static enum Interpolation {
			@LocalizedName(namespace="dataformat.image",key="Bilinear")
			BILINEAR,
			@LocalizedName(namespace="dataformat.image",key="Bicubic")
			BICUBIC,
			@LocalizedName(namespace="dataformat.image",key="Nearest")
			NEAREST
		}
		
		@LocalizedName(namespace="dataformat.image",key="Rescale type")
		public Type type = Type.ScaleFactor;
		
		@LocalizedName(namespace="dataformat.image",key="Interpolation")
		public Interpolation interpolation = Interpolation.BILINEAR;
		
		@OnlyIfEnum(name="type",value="ScaleFactor")
		public ScaleFactor scale = new ScaleFactor();
		@OnlyIfEnum(name="type",value="Fixed")
		public FixedSize fixed = new FixedSize();
		
		public static class ScaleFactor {
			@LocalizedName(namespace="dataformat.image",key="Scale factor")
			@DecimalGreaterThan(0)
			public float scaleFactor = 1;
		}
		
		public static class FixedSize {
			@LocalizedName(namespace="dataformat.image",key="Width")
			@IntegerMinimum(1)
			public int width = 100;
			@LocalizedName(namespace="dataformat.image",key="Height")
			@IntegerMinimum(1)
			public int height = 100;
		}
	}

	@Override
	public ILocalizableString getName() {
		return new LocalizableString("dataformat.image", "Rescale");
	}
	
	@Override
	public Class<BufferedImage> getType() {
		return BufferedImage.class;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		return new LocalizableString("dataformat.image", "Image");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/image/resize_picture_", ".png", 16, 32);
	
	@Override
	public IconProvider getIconProvider() {
		return iconProvider;
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
	public AsyncSupplier<Pair<BufferedImage,Object>, Exception> execute(BufferedImage input, Parameters params, Priority priority, WorkProgress progress, long work) {
		Task<Pair<BufferedImage,Object>, Exception> task = Task.cpu("Rescale image", priority, t -> {
			Object interpol;
			switch (params.interpolation) {
			case NEAREST: interpol = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR; break;
			case BICUBIC: interpol = RenderingHints.VALUE_INTERPOLATION_BICUBIC; break;
			default:
			case BILINEAR: interpol = RenderingHints.VALUE_INTERPOLATION_BILINEAR; break;
			}
			switch (params.type) {
			case ScaleFactor:
				RescaleOp op = new RescaleOp(params.scale.scaleFactor, 0, new RenderingHints(RenderingHints.KEY_INTERPOLATION, interpol));
				return new Pair<>(op.filter(input, null), null);
			case Fixed:
				BufferedImage resized = new BufferedImage(params.fixed.width, params.fixed.height, input.getType());
				Graphics2D g = resized.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpol);
				g.drawImage(input, 0, 0, params.fixed.width, params.fixed.height, 0, 0, input.getWidth(), input.getHeight(), null);
				g.dispose();
				return new Pair<>(resized,null);
			}
			if (progress != null) progress.progress(work);
			return null;
		});
		task.start();
		return task.getOutput();
	}
	
	@Override
	public void release(Pair<BufferedImage,Object> output) {
	}
	
}
