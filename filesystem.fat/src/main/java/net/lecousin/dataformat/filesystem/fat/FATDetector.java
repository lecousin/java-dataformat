package net.lecousin.dataformat.filesystem.fat;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;

public abstract class FATDetector implements DataFormatDetector.OnlyHeaderNeeded {

	public static class FAT12Detector extends FATDetector {

		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
				new Signature((short)0x36, new byte[] { 'F', 'A', 'T', '1', '2', ' ', ' ', ' ' }),
				new Signature((short)510, new byte[] { 0x55, (byte)0xAA })
			};
		}

		@Override
		public DataFormat[] getDetectedFormats() {
			return new DataFormat[] { FATDataFormat.FAT12DataFormat.instance };
		}

		@Override
		public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
			return FATDataFormat.FAT12DataFormat.instance;
		}
		
	}

	public static class FAT16Detector extends FATDetector {

		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
				new Signature((short)0x36, new byte[] { 'F', 'A', 'T', '1', '6', ' ', ' ', ' ' }),
				new Signature((short)510, new byte[] { 0x55, (byte)0xAA })
			};
		}

		@Override
		public DataFormat[] getDetectedFormats() {
			return new DataFormat[] { FATDataFormat.FAT16DataFormat.instance };
		}

		@Override
		public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
			return FATDataFormat.FAT16DataFormat.instance;
		}
		
	}

	public static class FAT32Detector extends FATDetector {

		@Override
		public Signature[] getHeaderSignature() {
			return new Signature[] {
				new Signature((short)0x36, new byte[] { 'F', 'A', 'T', '3', '2', ' ', ' ', ' ' }),
				new Signature((short)510, new byte[] { 0x55, (byte)0xAA })
			};
		}

		@Override
		public DataFormat[] getDetectedFormats() {
			return new DataFormat[] { FATDataFormat.FAT32DataFormat.instance };
		}

		@Override
		public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
			return FATDataFormat.FAT32DataFormat.instance;
		}
		
	}
	
}
