package edu.mit.csail.ada;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

public class AdaService extends Service {

	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	private SensorProcessor p;

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
		// int latencyInSec = parseUserInputs(registeredActivity);
		int latencyInSec = 15;
		p = new SensorProcessor(latencyInSec, 2);
		p.run();
	}

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
		p.stop();
		Toast.makeText(this, "Ada service stopped", Toast.LENGTH_SHORT).show();
	}

}
