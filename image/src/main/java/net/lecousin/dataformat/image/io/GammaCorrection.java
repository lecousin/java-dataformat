package net.lecousin.dataformat.image.io;

public class GammaCorrection implements PixelSampleModifier {

	public GammaCorrection(double gamma) {
		this.gamma = 1.0d / gamma;
	}
	
	private double gamma;
	
	@Override
	public int modifyPixelSample(int sample, int sampleBits) {
		int maxValue = (1 << sampleBits)-1;
		return (int)Math.round(Math.pow(((double)sample) / maxValue, gamma)*maxValue);
	}
	
}
