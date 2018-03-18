package net.lecousin.dataformat.compress;

import java.util.Collections;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.uidescription.resources.IconProvider;

public abstract class CompressedDataFormat implements DataFormat.DataContainerFlat {

	public static final IconProvider iconProvider = new IconProvider.FromPath("net.lecousin.dataformat.compress/images/zip_", ".png", 16, 24, 32, 48, 64, 128);

	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		AsyncWork<Data, Exception> get = getCompressedData(data);
		get.listenInline(() -> {
			if (get.isSuccessful() && get.getResult() != null)
				list.newElements(Collections.singleton(get.getResult()));
			list.done();
		});
	}
	
	public abstract AsyncWork<Data, Exception> getCompressedData(Data data);
	
}
