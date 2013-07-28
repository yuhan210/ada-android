package edu.mit.csail.ada;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.mit.csail.kde.KDE;
import edu.mit.csail.sensors.Accel;
import edu.mit.csail.sensors.GPS;
import edu.mit.csail.sensors.WiFi;

import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
/**
 * 
 * @author yuhan
 * SensorProcessor processes data from sensors and implements the fusing algorithm.
 *
 */

public class SensorProcessor {
	
	private int latency;// sec
	private Timer algoTimer;
	private ArrayList<Double> accelList = new ArrayList<Double>();
	
	
	/** The array used to store kde estimators (one for each activity)*/
	private KDE[][] accelKdeEstimators; //= new KDE[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM]; 
	private KDE[] wifiKdeEstimator;
	private KDE[] gpsKdeEstimator;
	
	public SensorProcessor(int latency){
		this.latency = latency;
		this.algoTimer = new Timer();
		
		accelKdeEstimators = new KDE[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
		wifiKdeEstimator = new KDE[Global.ACTIVITY_NUM];
		gpsKdeEstimator = new KDE[Global.ACTIVITY_NUM];
		for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
			accelKdeEstimators[i][0] = new KDE(Global.ACCEL_FEATURE_KDE_BW);
			accelKdeEstimators[i][1] = new KDE(Global.ACCEL_FEATURE_KDE_BW,0);
			accelKdeEstimators[i][2] = new KDE(Global.ACCEL_FEATURE_KDE_BW,0);
			wifiKdeEstimator[i] = new KDE(Global.WIFI_FEATURE_KDE_BW);
			gpsKdeEstimator[i] = new KDE(Global.GPS_FEATURE_KDE_BW, 0);
			
		}
		
		loadKDEClassifier();
		
		Accel.init();
		WiFi.init();
		GPS.init();
		
		run();

	}
	
	public void run(){
		Accel.start(SensorManager.SENSOR_DELAY_NORMAL);
		WiFi.start();
		GPS.start(15);
		
		algoTimer.schedule(new FusionAlgorithm(), latency * 1000);
	}
	
	public void stop(){
		Accel.stop();
		WiFi.stop();
		GPS.stop();
	}
	
	/**
	 * Load training data points from a file and generate KDE estimators  
	 */
	public void loadKDEClassifier(){
		AssetManager am = Global.context.getAssets();
		try {
			InputStream is = am.open(Global.accelTrainingDataFilename);
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			String line;
			
			// Read accel data points
			while ((line = r.readLine()) != null) {
			   String[] segs = line.split(",");
			   int gt = Integer.parseInt(segs[3]);
			   double mean = Double.parseDouble(segs[0]);
			   double sigma = Double.parseDouble(segs[1]);
			   double pf = Double.parseDouble(segs[2]);
			  
			   this.accelKdeEstimators[gt][0].addValue(mean, 1.0);
			   this.accelKdeEstimators[gt][1].addValue(sigma, 1.0);
			   this.accelKdeEstimators[gt][2].addValue(pf,1.0);
			   
			}
			is.close();
			r.close();
			
			// Read wifi data points
			is = am.open(Global.wifiTrainingDataFilename);
			r = new BufferedReader(new InputStreamReader(is));
			while ((line = r.readLine()) != null) {
				String[] segs = line.split(",");
				int gt = Integer.parseInt(segs[1]);
				double tanimotoDist = Double.parseDouble(segs[0]);
				this.wifiKdeEstimator[gt].addValue(tanimotoDist, 1.0);
			}
			is.close();
			r.close();
			
			// Read gps data points
			is = am.open(Global.gpsTrainingDataFilename);
			r = new BufferedReader(new InputStreamReader(is));
			while ((line = r.readLine()) != null) {
				String[] segs = line.split(",");d
				int gt = Integer.parseInt(segs[1]);
				double speed = Double.parseDouble(segs[0]);
				this.gpsKdeEstimator[gt].addValue(speed, 1.0);
			}
			is.close();
			r.close();
			
		} catch (IOException e) {
			Log.e(MainActivity.TAG, "Error open training data");
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @author yuhan
	 *
	 */
	class FusionAlgorithm extends TimerTask {
		
		@Override
		public void run() {
			
			double[] accelFeatures = new double[3];
			Accel.getFeatures(accelFeatures);
			Accel.clearAccelList();
			
			WiFi.scan();
			
			// Posterior prob from accelerometer
			double[][] accel_debug_bounded = new double[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
			double[][] accel_debug_unbounded = new double[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
			double[] accel_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] accel_post_unbounded = new double[Global.ACTIVITY_NUM];
			getAccelFeaturePostProb(accelFeatures, accel_debug_bounded, accel_debug_unbounded, accel_post_bounded, accel_post_unbounded);
			
			
		
			/**
			System.out.println(accelFeatures[0] + "," + accelFeatures[1]+ ","+ accelFeatures[2]);
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("Activity:" + i + ": unbounded:" + accel_debug_unbounded[i][0] + ", " + accel_debug_unbounded[i][1] + ", "+ accel_debug_unbounded[i][2]);
			}
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("Activity:" + i + ": bounded:" + accel_debug_bounded[i][0] + ", " + accel_debug_bounded[i][1] + ", "+ accel_debug_bounded[i][2]);
			}
			**/
			
			
			// Posterior prob from accelerometer WiFi
			double wifiFeature = WiFi.getFeature();
			double[] wifi_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] wifi_post_unbounded = new double[Global.ACTIVITY_NUM];
			
			if (wifiFeature != Global.INVALID_FEATURE){
				getPostProb(wifiKdeEstimator, wifiFeature, wifi_post_bounded, wifi_post_unbounded);
			}else{
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					wifi_post_bounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
					wifi_post_unbounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
				}
			}
			
			// Posterior prob from accelerometer GPS
			double gpsFeature = GPS.getFeature();
			double[] gps_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] gps_post_unbounded = new double[Global.ACTIVITY_NUM];
			
			if (gpsFeature != Global.INVALID_FEATURE){
				getPostProb(gpsKdeEstimator, gpsFeature, gps_post_bounded, gps_post_unbounded);
			}else{
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					gps_post_bounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
					gps_post_unbounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
					
				}
			}
			
			
			
			// Soft voting
			
			
			
			// EWMA smoothing
			
			
			//Maximum likelihood 
			int bounded_prediction = 0;
			int unbounded_prediction = 0;
			double bounded_maxlikelihood  = accel_post_bounded[0];
			double unbounded_maxlikelihood = accel_post_unbounded[0];
			for(int i = 1; i < Global.ACTIVITY_NUM; ++i){
				if (accel_post_bounded[i] > bounded_maxlikelihood){
					bounded_prediction = i;
					bounded_maxlikelihood = accel_post_bounded[i];
				}
				if (accel_post_unbounded[i] > unbounded_maxlikelihood){
					unbounded_prediction = i;
					unbounded_maxlikelihood = accel_post_unbounded[i];
				}
			}
			/**
			System.out.println("bounded: " + bounded_prediction + "\n"+ accel_post_bounded[0] + "," + accel_post_bounded[1] + "," +accel_post_bounded[2] + "," +accel_post_bounded[3] + "," +accel_post_bounded[4]);
			System.out.println("unbounded: " + unbounded_prediction + "\n" + accel_post_unbounded[0] + "," + accel_post_unbounded[1] + "," +accel_post_unbounded[2] + "," +accel_post_unbounded[3] + "," +accel_post_unbounded[4]);
			**/
		 
		}
		
		
		
		/**
		 * KDE evaluation for single feature sensors
		 * @param kdeEstimator 
		 * @param featureValue
		 * @param post_bounded
		 * @param post_unbounded
		 */
		public void getPostProb(KDE[] kdeEstimator, double featureValue, double[] post_bounded, double[] post_unbounded){
			double unbounded_denominator = 0;
			double bounded_denominator = 0;
			
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				post_bounded[i] = kdeEstimator[i].evaluate_unbounded(featureValue);
				post_unbounded[i] = kdeEstimator[i].evaluate_renorm(featureValue);
				
				unbounded_denominator += post_unbounded[i];
				bounded_denominator += post_bounded[i];
			}
			
			//Normalize
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				post_bounded[i] /= bounded_denominator;
				post_unbounded[i] /= unbounded_denominator;
				
			}
		}
		
		public void getAccelFeaturePostProb(double[] accelFeatures, double[][] accel_debug_bounded, double[][] accel_debug_unbounded, double[] accel_post_bounded, double[] accel_post_unbounded){
			double unbounded_denominator = 0;
			double bounded_denominator = 0;
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				accel_post_unbounded[i] = 1.0;
				accel_post_bounded[i] = 1.0;
				for(int j = 0; j < Global.ACCEL_FEATURE_NUM; ++j){
					accel_post_unbounded[i] *= accelKdeEstimators[i][j].evaluate_unbounded(accelFeatures[j]);
					accel_post_bounded[i] *= accelKdeEstimators[i][j].evaluate_renorm(accelFeatures[j]);
					
					accel_debug_unbounded[i][j] = accelKdeEstimators[i][j].evaluate_unbounded((accelFeatures[j]));			
					accel_debug_bounded[i][j] = accelKdeEstimators[i][j].evaluate_renorm((accelFeatures[j]));
					
				}	  			
				unbounded_denominator += accel_post_unbounded[i];
				bounded_denominator += accel_post_bounded[i];
			}
			
			// Normalize
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
				accel_post_unbounded[i] /= unbounded_denominator;
				accel_post_bounded[i] /= bounded_denominator;
			}
			
		}

	}
	
	
}
