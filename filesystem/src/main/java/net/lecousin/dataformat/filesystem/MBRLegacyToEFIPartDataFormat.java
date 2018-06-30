package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.progress.WorkProgress;

public class MBRLegacyToEFIPartDataFormat extends MBRDataFormat {

	public static final MBRLegacyToEFIPartDataFormat instance = new MBRLegacyToEFIPartDataFormat();
	
	protected MBRLegacyToEFIPartDataFormat() {
		
	}

	public Data getEFIPartSubData(Data container) {
		Data d = (Data)container.getProperty("EFIPartSubData");
		if (d != null)
			return d;
		d = new SubData(container, 512L, container.getSize() - 512, new FixedLocalizedString("EFI Part"));
		container.setProperty("EFIPartSubData", d);
		return d;
	}
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		return EFIPartDataFormat.instance.listenSubData(getEFIPartSubData(container), listener);
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
		EFIPartDataFormat.instance.unlistenSubData(getEFIPartSubData(container), listener);
	}
	
	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return EFIPartDataFormat.instance.getSubDataCommonProperties();
	}

	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return EFIPartDataFormat.instance.getSubDataCommonProperties(subData);
	}	
}
