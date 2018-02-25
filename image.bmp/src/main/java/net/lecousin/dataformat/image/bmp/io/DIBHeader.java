package net.lecousin.dataformat.image.bmp.io;

import java.awt.image.IndexColorModel;

import net.lecousin.framework.concurrent.synch.AsyncWork;

public class DIBHeader {

	public static enum Version {
		OS2_1, // OS/2 v1.x or Windows 2.x, BITMAPCOREHEADER
		OS2_2, // OS/2 v2.x
		WIN_3, // Windows 3
		WIN_3_PHOTOSHOP1,
		WIN_3_PHOTOSHOP2,
		WIN_4, // Windows 4
		WIN_5, // Windows 5
	}
	
	public Version version;
	public int width;
	public int height;
	public int planes;
	public int bitsPerPixel;
	
	public long compression = 0;
	public long bitmapSize = 0;
	public long horizRes = 0;
	public long vertRes = 0;
	public long colorsUsed = 0;
	public long importantColorsUsed = 0;
	
	public int resUnit = 0;
	public static final int RES_UNIT_PIXEL_PER_METER = 0;
	
	public int recording = 0;
	public static final int RECORDING_LEFT_TO_RIGHT_BOTTOM_UP = 0;
	
	public int rendering = 0;
	public static final int RENDERING_NO_HALFTONING = 0;
	public static final int RENDERING_ERROR_DIFFUSION_HALFTONING = 1;
	public static final int RENDERING_PANDA = 2;
	public static final int RENDERING_SUPER_CYCLE_HALFTONING = 3;
	public long renderingSize1 = 0;
	public long renderingSize2 = 0;
	
	public long colorEncoding = 0;
	public static final long COLOR_ENCODING_SCHEME_RGB = 0;
	
	public long appIdentifier = 0;
	
	public long redMask = 0;
	public long greenMask = 0;
	public long blueMask = 0;
	public long alphaMask = 0;

	public boolean bottomUp = true;
	
	public AsyncWork<IndexColorModel,Exception> palette = null;
	
}
