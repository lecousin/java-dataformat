package net.lecousin.dataformat.executable.windows.msdos;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class MZCompressedDataFormat extends MZDataFormat {

	public static class PKLITE extends MZCompressedDataFormat {
		
		public static final PKLITE instance = new PKLITE();
		
		@Override
		public ILocalizableString getName() {
			// TODO Auto-generated method stub
			return new FixedLocalizedString("MS-DOS Executable compressed with PKLITE");
		}
	}
	
	public static class LZ extends MZCompressedDataFormat {
		
		public static final LZ instance = new LZ();
		
		@Override
		public ILocalizableString getName() {
			// TODO Auto-generated method stub
			return new FixedLocalizedString("MS-DOS Executable compressed with LZEXE");
		}
	}
	
}
