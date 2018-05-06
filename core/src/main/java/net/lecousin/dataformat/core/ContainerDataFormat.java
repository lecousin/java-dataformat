package net.lecousin.dataformat.core;

import java.util.List;

import net.lecousin.dataformat.core.actions.CreateContainerDataAction;
import net.lecousin.dataformat.core.actions.CreateDataAction;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.actions.RemoveDataAction;
import net.lecousin.dataformat.core.actions.RenameDataAction;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.progress.FakeWorkProgress;
import net.lecousin.framework.progress.WorkProgress;

public interface ContainerDataFormat extends DataFormat {

	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener);
	
	public void unlistenSubData(Data container, CollectionListener<Data> listener);
	
	public Class<? extends DataCommonProperties> getSubDataCommonProperties();
	
	public DataCommonProperties getSubDataCommonProperties(Data subData);
	
	public default CreateDataAction<?, ?> getCreateNewDataAction(@SuppressWarnings("unused") Data containerData) {
		return null;
	}
	
	public default CreateContainerDataAction<?, ?> getCreateNewContainerDataAction(@SuppressWarnings("unused") Data containerData) {
		return null;
	}
	
	public default RenameDataAction<?, ?> getRenameSubDataAction(@SuppressWarnings("unused") Data subData) {
		return null;
	}
	
	public default RemoveDataAction<?> getRemoveSubDataAction(@SuppressWarnings("unused") List<Data> list) {
		return null;
	}
	
	public default List<DataAction<?, ?, ?>> getSubDataActions(@SuppressWarnings("unused") List<Data> list) {
		return null;
	}

	/** Marker to signal this is a directory inside a hierarchy. */
	public interface ContainerDirectory extends ContainerDataFormat {
		
		public default Data getRootDataContainer(Data data) {
			Data container = data.getContainer();
			while (container.getDetectedFormat() instanceof ContainerDirectory)
				container = container.getContainer();
			return container;
		}
		
	}
	
	public interface CacheSubData {
		
		public default long getCacheTimeout() {
			return 5*60*1000; // by default: 5 minutes
		}
		
		public WorkProgress listenSubData(Data container, CollectionListener<Data> listener);
		public void unlistenSubData(Data container, CollectionListener<Data> listener);
		
		static final String CACHE_KEY = "ContainerDataFormat.CacheSubData";
		
		@SuppressWarnings("unchecked")
		public static WorkProgress listenSubData(CacheSubData interf, Data container, CollectionListener<Data> listener) {
			CachedObject<CollectionListener.Keep<Data>> cache;
			CollectionListener.Keep<Data> subData = null;
			synchronized (container) {
				cache = (CachedObject<CollectionListener.Keep<Data>>)container.useCachedData(CACHE_KEY, listener);
				if (cache == null) {
					subData = new CollectionListener.Keep<Data>(new LinkedArrayList<Data>(10), Task.PRIORITY_NORMAL);
					CollectionListener.Keep<Data> sd = subData;
					cache = new CachedObject<CollectionListener.Keep<Data>>(subData, interf.getCacheTimeout()) {
						@Override
						protected void closeCachedObject(CollectionListener.Keep<Data> col) {
							interf.unlistenSubData(container, sd);
						}
					};
					container.setCachedData(CACHE_KEY, cache);
				}
			}
			if (subData != null) {
				WorkProgress p = interf.listenSubData(container, subData);
				subData.addListener(listener);
				return p;
			}
			cache.get().addListener(listener);
			return new FakeWorkProgress();
		}
		
		@SuppressWarnings({ "unchecked", "unused" })
		public static void unlistenSubData(CacheSubData interf, Data container, CollectionListener<Data> listener) {
			CachedObject<CollectionListener.Keep<Data>> cache;
			synchronized (container) {
				cache = (CachedObject<CollectionListener.Keep<Data>>)container.useCachedData(CACHE_KEY, listener);
				if (cache == null)
					return;
				cache.get().removeListener(listener);
				cache.release(listener);
				cache.release(listener);
			}
		}
		
	}
}
