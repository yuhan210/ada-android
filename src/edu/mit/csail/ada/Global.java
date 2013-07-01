package edu.mit.csail.ada;

import android.content.Context;

public class Global {
	/** Constants */
	public static final int ACTIVITY_NUM = 5;
	public static final int ACCEL_FEATURE_NUM = 3;
	public static final String trainingDataFilename = "train";
	public static final int ACCEL_TIMEWINDOW = 5; //SEC
	
	public static long startTime = 0;
	public static Context context;
	
	public static void setContext(Context ctx){
		context = ctx;
	}

}
