package net.lecousin.dataformat.image.ico;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.core.operations.search.SearchSingleDataToType;
import net.lecousin.dataformat.image.ImageDataFormat;
import net.lecousin.dataformat.image.ico.ICOCURFormat.Image;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Triple;

public abstract class ExtractBiggestImageOp<T extends ICOCURFormat> implements DataFormatReadOperation.OneToOne<ICOCURFormat, java.awt.Image, Void> {

	@Override
	public Class<Void> getParametersClass() {
		return Void.class;
	}

	@Override
	public Void createDefaultParameters() {
		return null;
	}

	@Override
	public Class<java.awt.Image> getOutputType() {
		return java.awt.Image.class;
	}
	
	@Override
	public IconProvider getOutputTypeIconProvider() {
		return ImageDataFormat.iconProvider;
	}
	
	@Override
	public ILocalizableString getOutputName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Image");
	}

	@Override
	public abstract T getInputFormat();
	
	public static class ICO extends ExtractBiggestImageOp<ICODataFormat> {
	
		@Override
		public ILocalizableString getName() {
			// TODO localize
			return new FixedLocalizedString("Extract biggest icon");
		}

		@Override
		public ICODataFormat getInputFormat() {
			return ICODataFormat.instance;
		}

	}

	public static class CUR extends ExtractBiggestImageOp<CURDataFormat> {
		
		@Override
		public ILocalizableString getName() {
			// TODO localize
			return new FixedLocalizedString("Extract biggest cursor");
		}

		@Override
		public CURDataFormat getInputFormat() {
			return CURDataFormat.instance;
		}

	}


	@Override
	public AsyncWork<Pair<java.awt.Image, Object>, ? extends Exception> execute(Data data, Void params, byte priority, WorkProgress progress, long work) {
		AsyncWork<Pair<java.awt.Image, Object>, Exception> result = new AsyncWork<>();
		WorkProgress subDataProgress = ((ICOCURFormat)data.getDetectedFormat()).listenSubData(data, new CollectionListener<Data>() {
			@Override
			public void elementsReady(Collection<? extends Data> elements) {
				if (elements.isEmpty()) {
					Exception error = new Exception("No image in ICO/CUR data");
					result.error(error);
					progress.error(error);
					return;
				}
				ArrayList<Data> list = new ArrayList<Data>(elements);
				Collections.sort(list, new Comparator<Data>() {
					@Override
					public int compare(Data o1, Data o2) {
						Image i1 = (Image)o1.getProperty(ICOCURFormat.IMAGE_PROPERTY);
						Image i2 = (Image)o2.getProperty(ICOCURFormat.IMAGE_PROPERTY);
						if (i1 == null)
							return i2 == null ? 0 : 1;
						if (i2 == null)
							return -1;
						if (i1.width > i2.width)
							return -1;
						if (i2.width > i1.width)
							return 1;
						return i1.height - i2.height; 
					}
				});
				extract(list, 0, priority, result, progress, work - work / 4);
			}

			@Override
			public void elementsAdded(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsRemoved(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsChanged(Collection<? extends Data> elements) {
			}

			@Override
			public void error(Throwable error) {
				Exception e = error instanceof Exception ? (Exception)error : new Exception(error);
				result.error(e);
				progress.error(e);
			}
		});
		WorkProgress.link(subDataProgress, progress, work / 4);
		return result;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void extract(List<Data> list, int index, byte priority, AsyncWork<Pair<java.awt.Image, Object>, Exception> result, WorkProgress progress, long work) {
		if (index == list.size()) {
			Exception error = new Exception("No image can be read from ICO/CUR data");
			result.error(error);
			progress.error(error);
			return;
		}
		Data data = list.get(index);
		long step = work / 5;
		AsyncWork<DataFormat, Exception> detect = data.detectFinalFormat(priority, progress, step);
		detect.listenInline(() -> {
			if (detect.hasError()) {
				extract(list, index + 1, priority, result, progress, work - step);
				return;
			}
			DataFormatReadOperation.OneToOne<?,java.awt.Image,?> op = SearchSingleDataToType.searchOneToOnePathToType(detect.getResult(), java.awt.Image.class);
			if (op == null) {
				extract(list, index + 1, priority, result, progress, work - step);
				return;
			}
			AsyncWork<Pair<java.awt.Image,Object>,? extends Exception> opResult = ((DataFormatReadOperation.OneToOne)op).execute(data, op.createDefaultParameters(), priority, progress, work - step);
			opResult.listenInlineSP(() -> {
				result.unblockSuccess(new Pair<>(opResult.getResult().getValue1(), new Triple<>(data, op, opResult.getResult())));
			}, result);
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void release(Data data, Pair<java.awt.Image, Object> output) {
		Triple<Data, DataFormatReadOperation.OneToOne, Pair<java.awt.Image, Object>>
			t = (Triple<Data, DataFormatReadOperation.OneToOne, Pair<java.awt.Image, Object>>)output.getValue2();
		t.getValue2().release(t.getValue1(), t.getValue3());
	}

}
