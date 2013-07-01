package edu.mit.csail.ada;

import edu.mit.csail.ada_lib.R;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
	public static final String TAG = "Main.java";
	
	/** Variables handling activity-service connection **/
	private Messenger mService = null;
	private boolean mIsBound;
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	private ServiceConnection mConnection = new ServiceConnection() 
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = new Messenger(service);
			Toast.makeText(MainActivity.this, "Service connected", Toast.LENGTH_SHORT).show();			
		}
		public void onServiceDisconnected(ComponentName className) 
		{
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			Toast.makeText(MainActivity.this, "Service disconnected", Toast.LENGTH_SHORT).show();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);	
		
		// initialize
		Global.startTime = System.nanoTime();
		doStartService();       
        doBindService();
		Global.setContext(this);
		
	}
	
	 private void doStartService(){
		ComponentName n = startService(new Intent(MainActivity.this, AdaService.class));
		if (n != null)
			System.out.println("started: " + n);
		else
			System.out.println("null service returned");		
	}	
	 
	private void doBindService(){
		// Establish a connection with the service.  We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		bindService(new Intent(MainActivity.this, AdaService.class), mConnection, BIND_AUTO_CREATE);
		mIsBound = true;
	}
	
	void doUnbindService() 
	{
		if (mIsBound) 
		{
			// If we have received the service, and hence registered with
			// it, then now is the time to unregister.
			if (mService != null) {}
			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
			System.out.println("Unbinding");
		}
	}	
	private void doStopService()
	{
		boolean stopped = stopService(new Intent(MainActivity.this, AdaService.class));
		System.out.println("stopped service: " + stopped);
	}
	
	 public void onDestroy()
	{
		super.onDestroy();
		doUnbindService();
		doStopService();
		System.out.println("destroyed");
	}
	
	 
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	class IncomingHandler extends Handler {
		@Override
	    public void handleMessage(Message msg) {}
	}
}
