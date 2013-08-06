package edu.mit.csail.ada;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.mit.csail.kde.KDE;
import edu.mit.csail.sensors.Accel;
import edu.mit.csail.sensors.AccelFeatureItem;
import edu.mit.csail.sensors.GPS;
import edu.mit.csail.sensors.WiFi;

import android.content.res.AssetManager;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

/**
 * 
 * @author yuhan
 * SensorProcessor processes data from sensors and implements the fusing algorithm.
 *
 */

public class SensorProcessor {
	
	private int latency;// sec
	private Handler algoHandler = new Handler();
	private Handler accelHandler = new Handler();
	private List<AccelFeatureItem> accelFeatureList = new ArrayList<AccelFeatureItem>();
	private int accelUpdateInterval = 5; // sec
	
	/** The array used to store kde estimators (one for each activity)*/
	private KDE[][] accelKdeEstimators; //= new KDE[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM]; 
	private KDE[] wifiKdeEstimator;
	private KDE[] gpsKdeEstimator;
	
	double[] activityBoundedConfidence = new double[Global.ACTIVITY_NUM];
	double[] activityUnboundedConfidence = new double[Global.ACTIVITY_NUM];
	
	// TO-DO: Set accel_only value based on user input
	private boolean accel_only = false;
	
	/** State machine - state 0: nothing, state 1: accelerometer only, state 2: accel + wifi, state 3: accel + gps**/
	private int state = 0;
	
	public SensorProcessor(int latency, int state){
		this.latency = latency;
		this.state = state;
		
		accelKdeEstimators = new KDE[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
		wifiKdeEstimator = new KDE[Global.ACTIVITY_NUM];
		gpsKdeEstimator = new KDE[Global.ACTIVITY_NUM];
		for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
			accelKdeEstimators[i][0] = new KDE(Global.ACCEL_FEATURE_KDE_BW);
			accelKdeEstimators[i][1] = new KDE(Global.ACCEL_FEATURE_KDE_BW,0);
			accelKdeEstimators[i][2] = new KDE(Global.ACCEL_FEATURE_KDE_BW,0);
			wifiKdeEstimator[i] = new KDE(Global.WIFI_FEATURE_KDE_BW, 0);
			gpsKdeEstimator[i] = new KDE(Global.GPS_FEATURE_KDE_BW, 0);
			activityBoundedConfidence[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
			activityUnboundedConfidence[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
		}
		
		loadKDEClassifier();
		Accel.init();
		WiFi.init();
		GPS.init();
	}
	
	public void run(){
		Accel.start(SensorManager.SENSOR_DELAY_NORMAL);
		WiFi.start();
		GPS.start(latency);
		
		accelHandler.removeCallbacks(accelTask);
		accelHandler.postDelayed(accelTask, 0);
		algoHandler.removeCallbacks(fusionAlgoTask);
        algoHandler.postDelayed(fusionAlgoTask, 0);
		//algoTimer.schedule(fusionAlgo, 0, latency * 1000);
	}
	
	public void stop(){
		accelHandler.removeCallbacks(accelTask);
		algoHandler.removeCallbacks(fusionAlgoTask);
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
				String[] segs = line.split(",");
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
	
	private Runnable accelTask = new Runnable(){

		@Override
		public void run() {
			
			accelHandler.postDelayed(accelTask, accelUpdateInterval * 1000);
			accelFeatureList.add(Accel.getFeatures());
			double[] adaptAccelFeatures = Accel.getFeaturesInArray();
			Accel.clearAccelList();
			
			// Adapt accelerometer sampling rate
			boolean ramp_up = false;
			for(int i = 0; i < adaptAccelFeatures.length; ++i){
				if(adaptAccelFeatures[i] >= 0.2 && adaptAccelFeatures[i] <= 0.8){
					ramp_up = true;
					break;
				}
			}
			if(ramp_up){
				Accel.changeSampleRate(SensorManager.SENSOR_DELAY_UI);
			}else{
				Accel.changeSampleRate(SensorManager.SENSOR_DELAY_NORMAL);
			}
			
		}
	
	};
	private Runnable fusionAlgoTask = new Runnable() {
		@Override
		public void run() {
			algoHandler.postDelayed(fusionAlgoTask, latency * 1000);
			long currentTime = System.nanoTime();
			WiFi.scan();
			
			/** Process accelerometer data **/
			// Keep recent accelFeatureItems and compute their average
			double[] aveAccelFeatures = new double[Global.ACCEL_FEATURE_NUM];
			for(int i = 0; i < Global.ACCEL_FEATURE_NUM; ++i){
				aveAccelFeatures[i] = 0.0;
			}
			int counter = 0;
			for(int i = 0; i < accelFeatureList.size(); ++i){
				if((currentTime - accelFeatureList.get(i).time) > latency * 1000000000){
					accelFeatureList.remove(i);
				}else{
					aveAccelFeatures[0] += accelFeatureList.get(i).mean;
					aveAccelFeatures[1] += accelFeatureList.get(i).std;
					aveAccelFeatures[2] += accelFeatureList.get(i).peakFreq;
					++counter;
					break;
				}
			}
			for(int i = 0; i > counter; ++i){
				aveAccelFeatures[i] /= (counter * 1.0);
			}
			
			
			// Posterior prob from accelerometer
			double[][] accel_debug_bounded = new double[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
			double[][] accel_debug_unbounded = new double[Global.ACTIVITY_NUM][Global.ACCEL_FEATURE_NUM];
			double[] accel_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] accel_post_unbounded = new double[Global.ACTIVITY_NUM];
			
			getAccelFeaturePostProb(aveAccelFeatures, accel_debug_bounded, accel_debug_unbounded, accel_post_bounded, accel_post_unbounded);
			int accel_prediction = getPrediction(accel_post_bounded);
			
			System.out.print("\nAccel- ");
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				System.out.print(accel_post_bounded[i] + ", ");
			}
		
			/**
			System.out.println(accelFeatures[0] + "," + accelFeatures[1]+ ","+ accelFeatures[2]);
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("Accel - Activity:" + i + ": unbounded:" + accel_debug_unbounded[i][0] + ", " + accel_debug_unbounded[i][1] + ", "+ accel_debug_unbounded[i][2]);
			}
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("Accel - Activity:" + i + ": bounded:" + accel_debug_bounded[i][0] + ", " + accel_debug_bounded[i][1] + ", "+ accel_debug_bounded[i][2]);
			}
			**/
			
			// Posterior prob from WiFi
			double wifiFeature = WiFi.getFeature();
			double[] wifi_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] wifi_post_unbounded = new double[Global.ACTIVITY_NUM];
			
			getPostProb(wifiKdeEstimator, wifiFeature, wifi_post_bounded, wifi_post_unbounded);
			
			System.out.print("\nWiFi- ");
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				System.out.print(wifi_post_bounded[i] + ", ");
			}
			
			/**
			System.out.println("wifiFeature:" + wifiFeature);
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("WiFi - Activity:" + i + ": unbounded:" + wifi_post_unbounded[i]);
			}
			for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.println("WiFi - Activity:" + i + ": bounded:" + wifi_post_bounded[i]);
			}**/
			
			// Posterior prob from GPS
			double gpsFeature = GPS.getFeature();
			double[] gps_post_bounded = new double[Global.ACTIVITY_NUM];
			double[] gps_post_unbounded = new double[Global.ACTIVITY_NUM];
			
			getPostProb(gpsKdeEstimator, gpsFeature, gps_post_bounded, gps_post_unbounded);
			
			System.out.print("\nGPS- ");
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				System.out.print(gps_post_bounded[i] + ", ");
			}
			
			
			// Make the prediction and adapt the sensors
			double[] curPostProb = new double[Global.ACTIVITY_NUM];
			if(accel_prediction == Global.STATIC || accel_prediction == Global.WALKING || accel_prediction == Global.RUNNING){ 
				
				// No need for soft voting, trust accelerometer
				curPostProb = accel_post_bounded;
				
			}else{
				
				// Combine results from other sensors
				// Soft voting
				double[] combined_bounded_post_prob = new double[Global.ACTIVITY_NUM]; 
				double[] combined_unbounded_post_prob = new double[Global.ACTIVITY_NUM]; 
				double unbounded_denominator = 0;
				double bounded_denominator = 0;
				for (int i = 0; i < Global.ACTIVITY_NUM; ++i){
					combined_bounded_post_prob[i] = accel_post_bounded[i] * wifi_post_bounded[i] * gps_post_bounded[i];
					combined_unbounded_post_prob[i] = accel_post_unbounded[i] * wifi_post_unbounded[i] * gps_post_unbounded[i];
					bounded_denominator += combined_bounded_post_prob[i];
					unbounded_denominator += combined_unbounded_post_prob[i];
				}
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					combined_bounded_post_prob[i] /= bounded_denominator;
					combined_unbounded_post_prob[i] /= unbounded_denominator;
				}
				System.out.print("\nsoft voting- ");
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					System.out.print(combined_bounded_post_prob[i] + ", ");
				}
				curPostProb = combined_bounded_post_prob;
			}

			
			// Finally - EWMA smoothing
			double bounded_denominator = 0.0;
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				activityBoundedConfidence[i] = (Global.EWMA_ALPHA * curPostProb[i]) + ((1-Global.EWMA_ALPHA) * activityBoundedConfidence[i]);
				bounded_denominator += activityBoundedConfidence[i];
			}
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				activityBoundedConfidence[i] /= bounded_denominator;
			}
			
			System.out.print("\nEWMA- ");
			for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
				System.out.print(activityBoundedConfidence[i] + ", ");
			}	
			int bounded_prediction = getPrediction(activityBoundedConfidence);
			
			System.out.println("\nbounded prediction: " + bounded_prediction );
			
			
			// Adapt sensors
			if(!accel_only){
				if(accel_prediction == Global.STATIC || accel_prediction == Global.WALKING || accel_prediction == Global.RUNNING){
					
					// Stop other sensors
					if(WiFi.isWorking()){
						WiFi.stop();
					}
					if(GPS.isWorking()){
						GPS.stop();
					}
				}else{
					
					// Accel is uncertain 
					// Turn on GPS when WiFi is low (but still keep sampling WiFi)
					// Turn off GPS when WiFi is high
					if (!WiFi.isWorking()){
						// WiFi is off, turn it on
						WiFi.start();
					}else if(WiFi.isDensityHigh()){ 
						// turn off GPS
						if (GPS.isWorking())
							GPS.stop();
						
					}else{ // turn on GPS
						if(!GPS.isWorking())
							GPS.start(latency);
					}
				}
			}
		}
		
		/**
		 * Get the maximum likelihood prediction
		 * @param postProb 
		 * @return prediction
		 */
		public int getPrediction(double[] postProb){
			int prediction = 0;
			double max = postProb[0];
			for(int i = 1; i < postProb.length; ++i){
				if(postProb[i] > max){
					max = postProb[i];
					prediction = i;
				}
			}
			return prediction;
		}
		
		/**
		 * KDE evaluation for single feature sensors
		 * @param kdeEstimator 
		 * @param featureValue
		 * @param post_bounded
		 * @param post_unbounded
		 */
		public void getPostProb(KDE[] kdeEstimator, double featureValue, double[] post_bounded, double[] post_unbounded){
			if(featureValue == Global.INVALID_FEATURE){
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					post_bounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
					post_unbounded[i] = 1 / (Global.ACTIVITY_NUM * 1.0);
					
				}
				return;
			}
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
			boolean isValid = true;
			
			for(int i = 0; i < accelFeatures.length; ++i){
				if (accelFeatures[i] == Global.INVALID_FEATURE){
					isValid = false;
					break;
				}
			}
			if(!isValid){
				for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
					for(int j = 0; j < Global.ACCEL_FEATURE_NUM; ++j){
						accel_debug_bounded[i][j] = 1/ (Global.ACTIVITY_NUM * 1.0);
						accel_debug_unbounded[i][j] = 1/ (Global.ACTIVITY_NUM * 1.0);
					}
					accel_post_bounded[i] = 1/ (Global.ACTIVITY_NUM * 1.0);
					accel_post_unbounded[i] = 1/ (Global.ACTIVITY_NUM * 1.0);
				}
				return;
			}
			
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
		
	};
}
