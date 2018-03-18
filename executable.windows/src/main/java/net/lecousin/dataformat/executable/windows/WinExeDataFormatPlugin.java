package net.lecousin.dataformat.executable.windows;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.dataformat.core.DataFormatPlugin;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.dataformat.core.actions.DataAction;
import net.lecousin.dataformat.core.operations.IOperation;
import net.lecousin.dataformat.executable.windows.coff.COFFDataFormat;
import net.lecousin.dataformat.executable.windows.coff.COFFDetector;
import net.lecousin.dataformat.executable.windows.msdos.MZCompressedDataFormat;
import net.lecousin.dataformat.executable.windows.msdos.MZDataFormat;
import net.lecousin.dataformat.executable.windows.msdos.MZDetector;
import net.lecousin.dataformat.executable.windows.msdos.MZSpecializationDetector;
import net.lecousin.dataformat.executable.windows.ne.NEDataFormat;
import net.lecousin.dataformat.executable.windows.ne.NEDetector;
import net.lecousin.dataformat.executable.windows.pe.PEDataFormat;
import net.lecousin.dataformat.executable.windows.pe.PEDetector;
import net.lecousin.dataformat.executable.windows.versioninfo.VersionInfoDataFormat;
import net.lecousin.dataformat.executable.windows.versioninfo.VersionInfoDetector;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class WinExeDataFormatPlugin implements DataFormatPlugin {

	public static final IconProvider winIconProvider = new IconProvider.FromPath("net/lecousin/dataformat/executable/windows/windows_", ".png", 16, 32, 48, 64, 128);
	public static final IconProvider dosIconProvider = new IconProvider.FromPath("net/lecousin/dataformat/executable/windows/terminal_", ".png", 16, 32, 48, 64, 128);
	
	@Override
	public DataFormat[] getFormats() {
		return new DataFormat[] {
			MZDataFormat.instance,
			PEDataFormat.instance,
			NEDataFormat.instance,
			COFFDataFormat.instance,
			VersionInfoDataFormat.instance,
			MZCompressedDataFormat.PKLITE.instance,
			MZCompressedDataFormat.LZ.instance
		};
	}
	
	@Override
	public DataFormatDetector[] getDetectors() {
		return new DataFormatDetector[] {
			new MZDetector(),
			new PEDetector(),
			new NEDetector(),
			new COFFDetector(),
			new VersionInfoDetector()
		};
	}
	
	@Override
	public DataFormatSpecializationDetector[] getSpecializationDetectors() {
		return new DataFormatSpecializationDetector[] {
			new PEDetector.InsideMZ(),
			new MZSpecializationDetector()
		};
	}

	@Override
	public DataAction[] getActions() {
		return new DataAction[0];
	}

	@Override
	public IOperation<?>[] getOperations() {
		return new IOperation<?>[] {};
	}
	
}
