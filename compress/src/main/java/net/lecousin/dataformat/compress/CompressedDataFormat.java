package net.lecousin.dataformat.compress;

import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

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
	
	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		return null;
	}

	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}
	
}
