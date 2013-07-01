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

	public static void init()
    {	
		sensorManager = (SensorManager)Global.context.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
    }
	

	/* Listeners and Events */
	public static void addListener(SensorEventListener listener, int period)
	{
		System.out.println(accelerometer);
		sensorManager.registerListener(listener, accelerometer, period); 
	}
	
	public static void removeListener(SensorEventListener listener)
	{
		sensorManager.unregisterListener(listener);
	}
}
