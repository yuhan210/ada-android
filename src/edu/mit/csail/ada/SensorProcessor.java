package edu.mit.csail.ada;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import edu.mit.csail.kde.KDE;
import edu.mit.csail.sensors.Accel;


import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
/**
 * 
 * @author yuhan
 * SensorProcessor processes data from sensors and implements the fusion algorithm.
 *
 */

public class SensorProcessor {
	private int latency;// sec
	private Timer timer;
	private ArrayList<Double> accelList = new ArrayList<Double>();
	
	/** The array used to store kde estimators (one for each activity)*/
	private KDE[][] kdeEstimators = new KDE[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM]; 
	
	public SensorProcessor(int latency){
		this.latency = latency;
		Accel.init();
		Accel.addListener(new AcclListener(), SensorManager.SENSOR_DELAY_NORMAL);
		
		this.timer = new Timer();
		this.accelList.clear();
		loadTrainingSet();
		
		
        timer.schedule(new FusionAlgorithm(), latency * 1000); 
        
	}
	
	/**
	 * Load training data points from a file and generate KDE estimators  
	 */
	public void loadTrainingSet(){
		AssetManager am = Global.context.getAssets();
		try {
			InputStream is = am.open(Global.trainingDataFilename);
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = r.readLine()) != null) {
			   String[] segs = line.split(",");
			   double mean = Double.parseDouble(segs[0]);
			   double sigma = Double.parseDouble(segs[1]);
			   double pf = Double.parseDouble(segs[2]);
			   int gt = Integer.parseInt(segs[3]);
			   
			   this.kdeEstimators[gt][0].addValue(mean, 1.0);
			   this.kdeEstimators[gt][1].addValue(sigma, 1.0);
			   this.kdeEstimators[gt][2].addValue(pf, 1.0);
			   
			}
			
		} catch (IOException e) {
			Log.e(MainActivity.TAG, "Error open training data");
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @author yuhan
	 * AccelListener is the callback of the Accelerometer sensor.
	 */
	private class AcclListener implements SensorEventListener 
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
	
	
	class FusionAlgorithm extends TimerTask {
		
		@Override
		public void run() {
			
			double[] accelFeatures = new double[3];
			extractAccelFeature(accelFeatures);
			double[] accel_prob = computeAccelProb(accelFeatures);
			System.out.println(accelFeatures[0] + "," + accelFeatures[1]+ ","+ accelFeatures[2]);
			
			timer.schedule(new FusionAlgorithm(), latency * 1000); 
		}
		
		public double[] computeAccelProb(double[] accelFeatures){
			double[] accel_posteriorProb = new double[5];
			
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				for(int j = 0; j < Global.ACCEL_FEATURE_NUM; ++j){
					kdeEstimators[i][j].logDensity(accelFeatures[j]);
				}
				 
				  			
			}
			
			
			return;
			
		}
		public void computeDFT(ArrayList<Double> accelList, double[] fftOutBuffer, int len) {
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
		
		public void extractAccelFeature(double[] f){
			double sum = 0.0;
			double sqSum = 0.0;
			int N = accelList.size();
			
			for (int i = 0; i < accelList.size(); ++i){
				double magItem = accelList.get(i);
				sum += magItem;
				sqSum += magItem * magItem;
			}
			
			double currentWindowFs = (N/ (double)(Global.ACCEL_TIMEWINDOW)); // sampling frequency
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
			
			accelList.clear();
		}
		
	}
	
	
}
