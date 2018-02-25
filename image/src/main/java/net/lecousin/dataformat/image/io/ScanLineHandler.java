package net.lecousin.dataformat.image.io;

public interface ScanLineHandler {

	public default void reset() {}
	
	public default int getBytesToReadPerLine(int width, int bitsPerPixel) {
		int bits = width*bitsPerPixel;
		return bits/8 + ((bits%8) == 0 ? 0 : 1);
	}
	
	@SuppressWarnings("unused")
	public default void scan(byte[] line, byte[] output, int outputOffset) throws InvalidImage {
		System.arraycopy(line, 0, output, outputOffset, line.length);
	}
	
}
