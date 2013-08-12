package edu.mit.csail.ada;

import edu.mit.csail.google.ActivityRecognitionScan;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class AdaService extends Service {

	private Messenger mMessenger = new Messenger(new IncomingHandler());
	private Handler UIHandler = new Handler();

	private SensorProcessor p;
	private ActivityRecognitionScan googleARScan;

	int latencyInSec = 15;

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	public boolean onUnbind(Intent intent) {
		System.out.println("Service onbound...");
		Toast.makeText(this, "Ada service onbound", Toast.LENGTH_SHORT).show();
		return false;
	}

	class IncomingHandler extends Handler {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Global.MSG_REGISTER_CLIENT:
				mMessenger = msg.replyTo;
				break;
			case Global.MSG_UNREGISTER_CLIENT:
				mMessenger = null;
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	public void onCreate() {
		boolean[] registeredActivity = new boolean[Global.ACTIVITY_NUM];
		for (int i = 0; i < Global.ACTIVITY_NUM; ++i) {
			registeredActivity[i] = false;
		}

		// TODO: Parse real user input
		// latencyInSec = parseUserInputs(registeredActivity);
		p = new SensorProcessor(latencyInSec, 2);
		p.run();
		googleARScan = new ActivityRecognitionScan(Global.context, latencyInSec);
		googleARScan.startActivityRecognitionScan();
		UIHandler.removeCallbacks(UIUpdateTask);
		UIHandler.postDelayed(UIUpdateTask, latencyInSec * 1000);
	}

	private Runnable UIUpdateTask = new Runnable() {

		@Override
		public void run() {
			UIHandler.postDelayed(UIUpdateTask, latencyInSec * 1000);
			try {
				Message msg = Message.obtain(null, Global.UPDATE_UI_MSG,
						Global.AdaPrediction, Global.GooglePrediction);
				mMessenger.send(msg);
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going
				// through the list from back to front so this is safe to do
				// inside the loop.
				Log.e("SensorProcessor", "mMessenger is dead");
				mMessenger = null;
			}
		}
	};

	/**
	 * Parses subscriptions and responsiveness from user and returns the
	 * latencyInSec
	 */
	public static int parseUserInputs(boolean[] registeredActivity) {

		// TODO: Parse real input
		for (int i = 0; i < registeredActivity.length; ++i) {
			registeredActivity[i] = true;
		}
		return 1;
	}

	public void onDestroy() {
		System.out.println("onDestroy()");
		UIHandler.removeCallbacks(UIUpdateTask);
		p.stop();
		googleARScan.stopActivityRecognitionScan();
		Toast.makeText(this, "Ada service stopped", Toast.LENGTH_SHORT).show();
	}

}
