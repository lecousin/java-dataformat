package net.lecousin.dataformat.core;

import java.util.ArrayList;
import java.util.Collections;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public interface DataWrapperDataFormat extends ContainerDataFormat {

	AsyncSupplier<Data, ? extends Exception> getWrappedData(Data container, WorkProgress progress, long work);
	
	@Override
	default WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Opening " + getName().appLocalizationSync());
		AsyncSupplier<Data, ? extends Exception> get = getWrappedData(container, progress, 950);
		get.onDone(
			(data) -> {
				if (data == null)
					listener.elementsReady(new ArrayList<>(0));
				else
					listener.elementsReady(Collections.singletonList(data));
				progress.done();
			},
			(error) -> {
				listener.error(error);
				progress.error(error);
				LCCore.getApplication().getDefaultLogger().error("Error opening " + DataWrapperDataFormat.this.getClass(), error);
			},
			(cancel) -> {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
			}
		);
		return progress;
	}
	
	@Override
	default void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
}
