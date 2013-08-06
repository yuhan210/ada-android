package edu.mit.csail.ada;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Environment;
import android.util.Log;

public class Debug {

	static final String msgDir = Environment.getExternalStorageDirectory().getPath()+ "/AdaLog/";
	static File file;
	static String fileName; 
	static FileWriter filewriter;
	static BufferedWriter out; 
	
	public static void init(){
		
		new File(msgDir).mkdir();
		// log file name
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");        
        String fileName = dateFormat.format(new Date());
		file = new File(msgDir, fileName);
		
		try {
			filewriter = new FileWriter(file);
		} catch (IOException e) {
			Log.e("TAG", "Could not write file " + e.getMessage());
		}
		out = new BufferedWriter(filewriter);
	}
	public static void logPrediction(int groundTruth, int prediction, double[] accel_post, double[] wifi_post, 
			double[] gps_post, double[] softVoting_prob, double[] ewma_prob, double[] accel_feature, double wifi_feature, double gps_feature, int state){
		String accelPostStr = "";
		String wifiPostStr = "";
		String gpsPostStr = "";
		String softVotingStr = "";
		String ewmaStr = "";
		String accelFeatureStr = "";
		
		DecimalFormat df = new DecimalFormat("#.####");    
		for(int i = 0; i < Global.ACTIVITY_NUM; ++i){
			if (i == 0){
				accelPostStr += df.format(accel_post[i]);
				wifiPostStr += df.format(wifi_post[i]);
				gpsPostStr += df.format(gps_post[i]);
				softVotingStr += df.format(softVoting_prob[i]);
				ewmaStr += df.format(ewma_prob[i]);
			}else{
				accelPostStr += "," + df.format(accel_post[i]);
				wifiPostStr += "," + df.format(wifi_post[i]);
				gpsPostStr += "," + df.format(gps_post[i]);
				softVotingStr += "," + df.format(softVoting_prob[i]);
				ewmaStr += "," + df.format(ewma_prob[i]);
			}
			
		}
		
		for(int i = 0; i < Global.ACCEL_FEATURE_NUM; ++i){
			if (i == 0){
				accelFeatureStr += df.format(accel_feature[i]);
			}else{
				accelFeatureStr += "," +  df.format(accel_feature[i]);
			}
		}
		
		try {
		        if (file.canWrite()) {
		        			                
		        	out.write(groundTruth + ";" + prediction + ";" +accelPostStr + ";" + wifiPostStr + ";"
		        			+ gpsPostStr+ ";" + accelFeatureStr + ";" + wifi_feature + ";" + gps_feature + "\n");
		        }
		    } catch (IOException e) {
		        
		    }
	}
	public static void stop(){
		try {
			filewriter.close();
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e("TAG", "Could not close file " + e.getMessage());
		}
		
	}
}
