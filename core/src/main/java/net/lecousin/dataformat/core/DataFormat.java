package net.lecousin.dataformat.core;

import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.uidescription.resources.IconProvider;

public interface DataFormat {
	
	public ILocalizableString getName();
	
	public AsyncWork<? extends DataFormatInfo,?> getInfo(Data data, byte priority);
	
	public IconProvider getIconProvider();
	
	public String[] getFileExtensions();
	public String[] getMIMETypes();
	
	public static interface DataContainerFlat extends DataFormat {
		@SuppressWarnings("unchecked")
		public default void getSubData(Data data, AsyncCollection<Data> list) {
			CachedObject<AsyncCollection.Keep<Data>> cache;
			AsyncCollection.Keep<Data> subData = null;
			synchronized (data) {
				cache = (CachedObject<AsyncCollection.Keep<Data>>)data.useCachedData("DataContainerFlat_SubData", DataContainerFlat.this);
				if (cache == null) {
					subData = new AsyncCollection.Keep<>();
					cache = new CachedObject<AsyncCollection.Keep<Data>>(subData, 5*60*1000) {
						@Override
						protected void closeCachedObject(AsyncCollection.Keep<Data> list) {
						}
					};
					cache.use(DataContainerFlat.this);
					data.setCachedData("DataContainerFlat_SubData", cache);
				}
			}
			if (subData != null)
				populateSubData(data, subData);
			cache.get().provideTo(list);
			CachedObject<AsyncCollection.Keep<Data>> c = cache;
			cache.get().ondone(new Runnable() {
				@Override
				public void run() {
					c.release(DataContainerFlat.this);
				}
			});
		}
		public void populateSubData(Data data, AsyncCollection<Data> list);
		
		public Class<? extends DataCommonProperties> getSubDataCommonProperties();
		public DataCommonProperties getSubDataCommonProperties(Data subData);
	}

	public static interface DataContainerHierarchy extends DataFormat, DataContainerFlat {
		
		public static interface Directory {
			public String getName();
			public Data getContainerData();
			
			public static class Impl implements Directory {
				public Impl(String name, Data container) {
					this.name = name;
					this.container = container;
				}
				public String name;
				public Data container;
				@Override
				public String getName() {
					return name;
				}
				@Override
				public Data getContainerData() {
					return container;
				}
			}
		}
		
		public void getSubDirectories(Data data, Directory parent, AsyncCollection<Directory> list);
		public void getSubData(Data data, Directory parent, AsyncCollection<Data> list);
		public String getSubDataPathSeparator();
	}
	
}
