package net.lecousin.dataformat.core;

public interface DataFormatListener {

	public void formatDetected(Data data, DataFormat format);
	public void endOfDetection(Data data);
	
	public void detectionError(Data data, Exception error);
	public void detectionCancelled(Data data);
	
}
