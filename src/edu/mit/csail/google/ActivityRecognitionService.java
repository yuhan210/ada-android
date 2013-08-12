package edu.mit.csail.google;

import com.google.android.gms.location.ActivityRecognitionResult;

import edu.mit.csail.ada.Global;

import android.app.IntentService;
import android.content.Intent;


public class ActivityRecognitionService extends IntentService {
	
	public ActivityRecognitionService() {
		super("ActivityRecognitionService");
	}

	/**
	 * Google Play Services calls this once it has analyzed the sensor data
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		if (ActivityRecognitionResult.hasResult(intent)) {
			ActivityRecognitionResult result = ActivityRecognitionResult
					.extractResult(intent);
			Global.GooglePrediction = result.getMostProbableActivity().getType();
			System.out.println("Google Result:" + Global.getGoogleFriendlyName(result.getMostProbableActivity()
					.getType()) + "\n" + result.toString());
		}
	}

	
}