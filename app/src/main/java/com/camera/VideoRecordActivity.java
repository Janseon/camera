package com.camera;

import com.android.camera.VideoRecorder;
//import com.duoyi.util.IntentUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class VideoRecordActivity extends VideoRecorder {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void startMovieView(String currentVideoFilename,
			Uri currentVideoUri) {
		openPlayVideoActivity(this, true, true,
				currentVideoFilename, -1);
		// openMovieView(this, currentVideoFilename, currentVideoUri);
	}
	
	public static void openPlayVideoActivity(Context context,
			boolean isInSDCard, boolean toSend, String path, float initScale) {
		Intent intent = new Intent(context, VideoPlayActivity.class);
		intent.putExtra("isInSDCard", isInSDCard);
		intent.putExtra("toSend", toSend);
		intent.putExtra("path", path);
		intent.putExtra("initScale", initScale);
		context.startActivity(intent);
		((Activity) context).overridePendingTransition(0, 0);
	}

}
