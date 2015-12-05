package com.camera;

import com.android.camera.MovieView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;

public class VideoPlayActivity extends MovieView {
	boolean isInSDCard = true;
	boolean toSend = true;
	float initScale = -1;
	String url;
	String protraitPath;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Intent intent = getIntent();
		isInSDCard = intent.getBooleanExtra("isInSDCard", true);
		toSend = intent.getBooleanExtra("toSend", true);
		initScale = intent.getFloatExtra("initScale", -1);
		super.onCreate(savedInstanceState);
		if (isInSDCard) {
			protraitPath = intent.getStringExtra("path");
			mUri = filePath2Uri(protraitPath);
			set2Play();
		} else {
			url = intent.getStringExtra("path");
			getVideoAndPlay();
		}
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, 0);
	}

	// ////获取视频
	String filePath;

	private void getVideoAndPlay() {
		// filePath = AsyncMediaAPI.getHttpFile(url,
		// new GetAudioFileAsyncListener(), progressRunnable);
		// if (filePath == null) {
		// setLoading(true);
		// } else {
		// setPlayPathAndPlay();
		// }
	}

	private void setPlayPathAndPlay() {
		protraitPath = filePath;
		// isInSDCard = true;
		mUri = filePath2Uri(protraitPath);
		set2Play();
	}

	private Uri filePath2Uri(String path) {
		// return Uri.parse("file://" + path);
		return Uri.parse(path);
	}

	String loadingString = null;

	// ProgressRunnable progressRunnable = new ProgressRunnable() {
	// @Override
	// public void run() {
	// if (loadingString == null) {
	// loadingString = loadingText.getText().toString();
	// }
	// int percent = curProgress * 100 / maxProgress;
	// String tips = "\n视频大小为: "
	// + VideoUtil.length2SizeString(maxProgress) + "  " + percent
	// + "%";
	// loadingText.setText(loadingString + tips);
	// }
	// };
	//
	// class GetAudioFileAsyncListener extends AsyncListener {
	// @Override
	// public void onDone() {
	// super.onDone();
	// setLoading(false);
	// filePath = (String) getObject();
	// if (filePath != null) {
	// setPlayPathAndPlay();
	// }
	// }
	//
	// @Override
	// public void onException() {
	// super.onException();
	// setLoading(false);
	// alert(getAlertMsg("下载视频文件失败"));
	// }
	// }

	private void setLoading(boolean loading) {
		if (loading) {
			// rightImage.setImageResource(R.anim.loading);
			// AnimationDrawable animationDrawable = (AnimationDrawable)
			// rightImage
			// .getDrawable();
			// animationDrawable.start();
			// backImage.setEnabled(false);
			// playImage.setEnabled(false);
			// forwardImage.setEnabled(false);
		} else {
			// rightImage.setImageResource(R.drawable.icon_more_add);
			// backImage.setEnabled(true);
			// playImage.setEnabled(true);
			// forwardImage.setEnabled(true);
		}
	}

	@Override
	protected void onSetButtons(MediaController mediaController) {
		// super.onSetButtons(mediaController);
		int margin = (int) (5 * 1.5f);
		int width = (int) (45 * 1.5f);

		FrameLayout.LayoutParams useFrameParams = new FrameLayout.LayoutParams(
				width, ViewGroup.LayoutParams.WRAP_CONTENT);
		useFrameParams.gravity = Gravity.RIGHT;
		useFrameParams.setMargins(margin, margin, margin, margin);
		Button useButton = new Button(mediaController.getContext());
		useButton.setText("完成");
		// useButton.setBackgroundResource(R.drawable.button_blue_selector);
		useButton.setOnClickListener(backOnClickListener);
		mediaController.addView(useButton, useFrameParams);
		useButton.setPadding(margin, 0, margin, 0);
	}

	OnClickListener anewOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			// IntentUtil.openVideoRecordActivity(VideoPlayActivity.this);
			finish();
		}
	};
	OnClickListener useOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Bundle extras = new Bundle();
			extras.putString("protraitPath", mUri.toString());
			String time = toSecond(getDuration()) + "";
			extras.putString("time", time);
			// BroadcastUtil
			// .sendBroadcast(extras, BroadcastUtil.ACTION_SEND_VIDEO);
			finish();
		}
	};
	OnClickListener backOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			finish();
		}
	};

	public int toSecond(int time) {
		return time / 1000;
	}
}
