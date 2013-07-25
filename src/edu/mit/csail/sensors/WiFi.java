package edu.mit.csail.sensors;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import edu.mit.csail.ada.Global;


public class WiFi {
	private static WifiManager wifiManager;
	private static WifiReceiver wifiReceiver = new WifiReceiver();
	private static List<ArrayList<ScanResult>> scanResults = new ArrayList<ArrayList<ScanResult>>(2);
	private static double distance = 0.0;
	
	public static void init(){
		wifiManager = (WifiManager)Global.context.getSystemService(Context.WIFI_SERVICE);
		Global.context.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
	}
	
	public static void scan(){
		wifiManager.startScan();
	}
	
	public static void stop()
	{
		Global.context.unregisterReceiver(wifiReceiver);
	}
	
	public static double getDistance(){
		return distance;
	}
	static class WifiReceiver extends BroadcastReceiver {
	  	 public void onReceive(Context c, Intent intent) {  		
	  		ArrayList<ScanResult> curScanResult = (ArrayList<ScanResult>) wifiManager.getScanResults();
	  		System.out.println("wifi received + " + curScanResult.size());
	  		for (int i = 0; i < curScanResult.size(); i++)
	    	{
	    		System.out.println(curScanResult.get(i).SSID + "," + curScanResult.get(i).BSSID + "," + curScanResult.get(i).level);
	    	}
	  		
	  		if (scanResults.size() > 0){
	  			scanResults.remove(0);
	  		}
	  		scanResults.add(curScanResult);
	  		distance = updateDistance();
		 }
	}
	
	private static double updateDistance(){		
		LinkedHashSet<String> aPDimHashSet = new LinkedHashSet<String>();
		
		if (scanResults.size() > 0){			
			for(int i = 0; i < scanResults.size(); ++i){
				for (int j = 0; j < scanResults.get(i).size(); ++j){
					aPDimHashSet.add(scanResults.get(i).get(j).BSSID);						
				}				
			}
			
			int scanNum = scanResults.size();
			int obAPNum = aPDimHashSet.size();
			List<String> apDimlist = new ArrayList<String>(aPDimHashSet);			
			double[][] scanVect = new double[scanNum][obAPNum];
			for(int i = 0; i < scanNum; ++i){
				List<ScanResult> scanList = scanResults.get(i);				
				for(int j = 0; j < obAPNum; ++j){
					String macAdd = apDimlist.get(j);
					
					for(int k = 0; k < scanList.size(); ++k){					
						if(scanList.get(k).BSSID == macAdd){
							scanVect[i][j] = scanList.get(k).level * 1.0;								
						}else{
							scanVect[i][j] = 0.0; 								
						}
					}					
				}
			}
		
			double[] distance = new double[scanNum - 1];
			for(int i = 0; i < (scanNum - 1); ++i){
				double f1f2, f12, f22;
				f1f2 = f12 = f22 = 0.0;
				
				for(int j = 0; j < obAPNum; ++j){
					f1f2 += scanVect[i][j] * scanVect[i+1][j];
					f12 += scanVect[i][j] * scanVect[i][j];
					f22 += scanVect[i+1][j] * scanVect[i+1][j];									
				}
				distance[i] = f1f2 / (f12 + f22 - f1f2);				
			}
			
			double aveDist = 0.0;
			for(int i = 0; i < (scanNum - 1); ++i){
				aveDist += distance[i];
			}
			
			return aveDist/ ((scanNum - 1) * 1.0);
		}
		return -1;
	}
	
}
