package edu.mit.csail.sensors;

import edu.mit.csail.ada.Global;
import android.content.Context;
import android.hardware.Sensor;
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

	public static void init(SensorEventListener listener, int period)
    {	
		sensorManager = (SensorManager)Global.context.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
		sensorManager.registerListener(listener, accelerometer, period); 
    }
	

	public static void stop(SensorEventListener listener)
	{
		sensorManager.unregisterListener(listener);
	}
}
