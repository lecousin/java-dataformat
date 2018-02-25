package net.lecousin.dataformat.executable.windows.msdos;

import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class MZDataFormat implements DataFormat.DataContainerFlat {

	public static final MZDataFormat instance = new MZDataFormat();
	
	protected MZDataFormat() {}
	
	// http://www.fileformat.info/format/exe/corion-mz.htm
	// http://www.delorie.com/djgpp/doc/exe/
	// http://wiki.osdev.org/MZ
	// https://en.wikibooks.org/wiki/X86_Disassembly/Windows_Executable_Files#MS-DOS_EXE_Files
	// TODO specializations from the first link

	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("MS-DOS Executable");
	}
	
	@Override
	public IconProvider getIconProvider() { return WinExeDataFormatPlugin.dosIconProvider; }
	
	@Override
	public String[] getMIMETypes() {
		return new String[] {
			"application/msdos-windows",
			"application/dos-exe"
		};
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] { "exe" };
	}
	
	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		SubData sd = (SubData)data.getProperty("MZSubData");
		if (sd == null) {
			Integer offset = (Integer)data.getProperty("MZDataOffset");
			if (offset == null) {
				list.done();
				return;
			}
			int off = offset.intValue();
			long size = data.getSize();
			if (off == size) {
				list.done();
				return;
			}
			sd = new SubData(data, off, size-off, "Data");
			data.setProperty("MZSubData", sd);
		}
		list.newElements(Collections.singletonList(sd));
		list.done();
	}
	
	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		return null;
	}
	
	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}
	
	@Override
	public ISynchronizationPoint<Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}
}
