package net.lecousin.dataformat.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import net.lecousin.framework.memory.CacheManager;
import net.lecousin.framework.memory.CachedObject;
import net.lecousin.framework.memory.MemoryManager;
import net.lecousin.framework.util.Pair;

public class DataManager implements CacheManager {

	static DataManager getInstance() {
		if (instance == null) instance = new DataManager();
		return instance;
	}
	
	private static DataManager instance = null;
	private DataManager() {
		MemoryManager.register(this);
	}
	
	private HashMap<Data,HashMap<String,CachedObject<?>>> dataCachedData = new HashMap<>(100);
	private HashMap<CachedObject<?>,Pair<Data,String>> cachedMapping = new HashMap<>(200);
	
	void releaseAll(Data data) {
		synchronized (dataCachedData) {
			HashMap<String,CachedObject<?>> cache = dataCachedData.remove(data);
			if (cache == null) return;
			for (CachedObject<?> o : cache.values()) {
				cachedMapping.remove(o);
				o.close();
			}
		}
	}
	
	CachedObject<?> useCachedData(Data data, String key, Object user) {
		synchronized (dataCachedData) {
			HashMap<String,CachedObject<?>> cache = dataCachedData.get(data);
			if (cache == null) return null;
			CachedObject<?> o = cache.get(key);
			if (o == null) return null;
			o.use(user);
			return o;
		}
	}
	
	<T> void setCachedData(Data data, String key, CachedObject<T> cachedData) {
		synchronized (dataCachedData) {
			HashMap<String,CachedObject<?>> cache = dataCachedData.get(data);
			if (cache == null) {
				cache = new HashMap<String,CachedObject<?>>(10);
				dataCachedData.put(data, cache);
			}
			CachedObject<?> prev = cache.put(key, cachedData);
			if (prev != null) {
				prev.close();
				cachedMapping.remove(prev);
			}
			cachedMapping.put(cachedData, new Pair<>(data, key));
		}
	}
	
	@Override
	public String getDescription() {
		return "Data cache manager ("+cachedMapping.size()+" items)";
	}
	@Override
	public List<String> getItemsDescription() {
		ArrayList<String> list = new ArrayList<>(cachedMapping.size());
		synchronized (dataCachedData) {
			for (CachedObject<?> o : cachedMapping.keySet()) {
				list.add(o.get().getClass().getSimpleName()+": "+o.get().toString()+" ("+o.cachedDataCurrentUsage()+" users) (last usage "+(System.currentTimeMillis()-o.cachedDataLastUsage())+"ms. ago)");
			}
		}
		return list;
	}
	
	@Override
	public Collection<? extends CachedData> getCachedData() {
		synchronized (dataCachedData) {
			return cachedMapping.keySet();
		}
	}
	
	@Override
	public boolean free(CachedData data) {
		CachedObject<?> o = (CachedObject<?>)data;
		o.close();
		synchronized (dataCachedData) {
			Pair<Data,String> mapping = cachedMapping.remove(o);
			HashMap<String,CachedObject<?>> cache = dataCachedData.get(mapping.getValue1());
			cache.remove(mapping.getValue2());
			if (cache.isEmpty())
				dataCachedData.remove(mapping.getValue1());
		}
		return true;
	}
	
	@Override
	public long getCachedDataExpiration(CachedData data) {
		return ((CachedObject<?>)data).getExpiration();
	}
	
}
