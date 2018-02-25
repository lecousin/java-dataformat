package net.lecousin.dataformat.archive.rar;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public abstract class RarDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { RarDataFormat.instance };
	}
	
	public static class OldFormat extends RarDetector {
		
		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
				new Signature((short)0, new byte[] { 0x52, 0x45, 0x7E, 0x5E })
			};
		}
		
	}
	
	public static class Version1_5 extends RarDetector {
		
		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
				new Signature((short)0, new byte[] { 'R', 'a', 'r', '!', 0x1A, 7, 0 })
			};
		}
		
	}
	
	public static class Version5 extends RarDetector {
		
		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
					new Signature((short)0, new byte[] { 'R', 'a', 'r', '!', 0x1A, 7, 1, 0 })
			};
		}
		
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		return RarDataFormat.instance;
	}
	
}
