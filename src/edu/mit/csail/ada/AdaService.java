package edu.mit.csail.ada;

import edu.mit.csail.sensors.Accel;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

public class AdaService extends Service{

	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	/** Sensors*/
	private Accel accel = new Accel();
	
	private SensorProcessor p;
	
	@Override
	public IBinder onBind(Intent intent) {
		 return mMessenger.getBinder();
	}
	public boolean onUnbind(Intent intent){
		System.out.println("Service onbound...");
		Toast.makeText(this, "Ada service onbound", Toast.LENGTH_SHORT).show();
		return false;
	}
	class IncomingHandler extends Handler{
		 public void handleMessage(Message msg) {
	           switch(msg.what){
	           	    default: 
	            	    super.handleMessage(msg);       	   
	           }
		 }
	}
	
	public void onCreate(){
	
		parseUserInputs();
		initSensors();
		p = new SensorProcessor(5);
	}
	/**
	 * Determines the set of sensors to use; and their sampling rate
	 */
	public static void initSensors(){}
	
	/**
	 * Parses subscriptions and responsiveness from user
	 */
	public static void parseUserInputs(){}

	
	public void onDestroy() 
    {
		p.stop();
		Toast.makeText(this, "Ada service stopped", Toast.LENGTH_SHORT).show();
    }

}
