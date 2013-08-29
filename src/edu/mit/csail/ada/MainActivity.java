package edu.mit.csail.ada;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import edu.mit.csail.ada_lib.R;

public class MainActivity extends Activity {
	public static final String TAG = "MainActivity";

	/** UI Variables **/
	private Spinner gt_spinner;
	private Button btnSubmit;
	private List<String> spinList = new ArrayList<String>();
	private TextView adaTextView;
	private TextView googleTextView;

	/** Variables handling activity-service connection **/
	private Messenger mService = null;
	private boolean mIsBound;
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = new Messenger(service);
			try {
				Message msg = Message.obtain(null, Global.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
			}
			Toast.makeText(MainActivity.this, "Service connected",
					Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			Toast.makeText(MainActivity.this, "Service disconnected",
					Toast.LENGTH_SHORT).show();
		}
	};

	private boolean hasInitialized = false;
	private boolean resumeHasRun = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		 if (hasInitialized) return;
		    
		hasInitialized = true;
		setContentView(R.layout.main);

		// initialize
		Global.startTime = System.nanoTime();
		addItemsOnSpinner();
		addListenerOnButton();
		addListenerOnSpinnerItemSelection();
		doStartService();
		doBindService();
		Global.setContext(this);
		adaTextView = (TextView) findViewById(R.id.adaPrediction);
		googleTextView = (TextView) findViewById(R.id.googlePrediction);

	}

	protected void onResume(){
		super.onResume();
		if (resumeHasRun){
			
			updatePredictionOnUI(Global.AdaPrediction, Global.GooglePrediction);
			
		}else{ // first time start the app
			resumeHasRun = true;
			return;
		}
	}
	private void doStartService() {
		ComponentName n = startService(new Intent(MainActivity.this,
				AdaService.class));
		if (n != null)
			System.out.println("started: " + n);
		else
			System.out.println("null service returned");
	}

	private void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		if (bindService(new Intent(MainActivity.this, AdaService.class),
				mConnection, BIND_AUTO_CREATE)) {
			mIsBound = true;
		}
	}

	void doUnbindService() {
		if (mIsBound) {
			// If we have received the service, and hence registered with
			// it, then now is the time to unregister.
			if (mService != null) {
				try {
					Message msg = Message.obtain(null,
							Global.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) {
					// There is nothing special we need to do if the service
					// has crashed.
				}
			}
			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
			System.out.println("Unbinding");
		}
	}

	private void doStopService() {
		boolean stopped = stopService(new Intent(MainActivity.this,
				AdaService.class));
		System.out.println("stopped service: " + stopped);
	}

	public void onDestroy() {
		super.onDestroy();
		doUnbindService();
		doStopService();
		System.out.println("destroyed");
	}

	// add items into spinner dynamically
	public void addItemsOnSpinner() {
		gt_spinner = (Spinner) findViewById(R.id.gt_spinner);
		spinList.add("Static");
		spinList.add("Walking");
		spinList.add("Running");
		spinList.add("Biking");
		spinList.add("Driving");
		spinList.add("Unknown");

		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, spinList);
		dataAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		gt_spinner.setAdapter(dataAdapter);
	}

	public void addListenerOnSpinnerItemSelection() {
		gt_spinner = (Spinner) findViewById(R.id.gt_spinner);

	}

	// get the selected dropdown list value
	public void addListenerOnButton() {

		gt_spinner = (Spinner) findViewById(R.id.gt_spinner);
		btnSubmit = (Button) findViewById(R.id.btnSubmit);
		btnSubmit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Global.setGroundTruth(String.valueOf(gt_spinner
						.getSelectedItem()));
				Toast.makeText(
						Global.context,
						"OnClickListener : " + "\nSpinner: "
								+ String.valueOf(gt_spinner.getSelectedItem()),
						Toast.LENGTH_SHORT).show();
			}

		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void updatePredictionOnUI(int adaPrediction, int googlePrediction) {
		adaTextView.setText(Global.getAdaFriendlyGroundTruth(adaPrediction));
		googleTextView.setText(Global.getGoogleFriendlyName(googlePrediction));
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Global.UPDATE_UI_MSG:
				updatePredictionOnUI(msg.arg1, msg.arg2);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
}
