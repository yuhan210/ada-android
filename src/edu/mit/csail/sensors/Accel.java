package edu.mit.csail.sensors;

import java.util.ArrayList;

import edu.mit.csail.ada.Global;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
/**
 * 
 * @author yuhan
 * 
 */
public class Accel {
	private static SensorManager sensorManager;
	private static Sensor accelerometer;
	private static AcclListener accelListerner = new AcclListener();
	private static ArrayList<Double> accelList = new ArrayList<Double>();
	
	public static void init()
    {	
		sensorManager = (SensorManager)Global.context.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
		
    }
	
	public static void start(int period){
		sensorManager.registerListener(accelListerner, accelerometer, period); 
		clearAccelList();
	}
	
	/**
	 * 
	 * @author yuhan
	 * AccelListener is the callback of the Accelerometer sensor.
	 */
	private static class AcclListener implements SensorEventListener 
    {
		int accuracy = 0;
		public void onSensorChanged(SensorEvent event)
        {   
			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];			
			double mag = (double) Math.sqrt(x*x+y*y+z*z);
			accelList.add(mag);
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) 
        {
        	this.accuracy = accuracy;
        }
    }
	
	/**
	 * Extract accelerometer features (mean, std, peak freq) from the current accelerometer window (accelList)
	 * @param f an array contains three accelerometer features
	 */
	public static void getFeatures(double[] f){
		double sum = 0.0;
		double sqSum = 0.0;
		int N = accelList.size();
		
		for (int i = 0; i < accelList.size(); ++i){
			double magItem = accelList.get(i);
			sum += magItem;
			sqSum += magItem * magItem;
		}
		
		double currentWindowFs = (N/ (double)(Global.ACCEL_TIMEWINDOW_SEC)); // sampling frequency
		double currentWindowFFT[] = new double[((N/2) + 1)];  
		computeDFT(accelList,currentWindowFFT, N); 

		double peakPower = -Double.MAX_VALUE;
		int peakPowerLocation = -1;
		for (int j = 1; j < currentWindowFFT.length ; ++j) {
			if (currentWindowFFT[j] > peakPower) {
				peakPower = currentWindowFFT[j];
				peakPowerLocation = j;
			}
		}
		
		f[0] = sum/(N * 1.0);//mean
		f[1] = Math.sqrt( (sqSum - (sum * sum)/(N * 1.0))/((N * 1.0)-1.0));//standard dev
		f[2] = peakPowerLocation/(N * 1.0) * currentWindowFs; //peak freq
	}
	
	/**
	 * Transform the accelList into the frequency domain
	 * @param accelList: array in the time domain
	 * @param fftOutBuffer: output array in the frequency domain
	 * @param len
	 */
	private static void computeDFT(ArrayList<Double> accelList, double[] fftOutBuffer, int len) {
		int N = len;
		for (int i = 1; i < (N/2 + 1); ++i) {
			double realPart = 0;
			double imgPart = 0;
			for (int j = 0; j < N; ++j) {
				realPart += (accelList.get(j) * Math.cos(-(2.0 * Math.PI * i * j)/N));
				imgPart +=  (accelList.get(j) * Math.sin(-(2.0 * Math.PI * i * j)/N));
			}
			realPart /= N;
			imgPart /= N;
			fftOutBuffer[i] = 2 * Math.sqrt(realPart * realPart + imgPart * imgPart);
		}
	}
	
	/**
	 * Update the sampling rate
	 */
	public static void updateSamplingPeriod(int period){
		stop();
		sensorManager.registerListener(accelListerner, accelerometer, period); 
	}
	
	/**
	 * Clear accelList
	 */
	public static void clearAccelList(){
		accelList.clear();
	}
	
	/**
	 * Stop the Accelerometer sensor 
	 */
	public static void stop()
	{
		sensorManager.unregisterListener(accelListerner);
	}
}
