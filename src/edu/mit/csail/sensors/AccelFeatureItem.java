package edu.mit.csail.sensors;

public class AccelFeatureItem {

	public long time;
	public double mean;
	public double std;
	public double peakFreq;

	public AccelFeatureItem() {
	}

	public AccelFeatureItem(long time, double mean, double std, double peakFreq) {
		this.time = time;
		this.mean = mean;
		this.std = std;
		this.peakFreq = peakFreq;
	}

	public String toString() {
		return "time:" + time + ", mean:" + mean + ", std:" + std
				+ ",peakFreq:" + peakFreq;
	}
}
