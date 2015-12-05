package com.android.camera;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import com.camera.R;

/**
 * This activity plays a video from a specified URI.
 */
public class MovieView extends Activity {
	@SuppressWarnings("unused")
	private static final String TAG = "MovieView";

	View rootView;
	private MovieViewControl mControl;
	protected boolean mFinishOnCompletion;
	protected Uri mUri;
	private MovieView _this;
	protected TextView loadingText;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		_this = this;
		setContentView(R.layout.movie_view);
		rootView = findViewById(R.id.root);
		loadingText = (TextView) findViewById(R.id.loading_text);
		// Intent intent = getIntent();
		// mUri = intent.getData();
		// if (mUri == null) {
		// Toast.makeText(this, "不存在该视频地址", 0).show();
		// return;
		// }
		// mControl = new MovieViewControl(rootView, this, mUri) {
		// @Override
		// public void onCompletion() {
		// _this.onCompletion();
		// }
		//
		// @Override
		// public void onSetButtons(MediaController mediaController) {
		// _this.onSetButtons(mediaController);
		// }
		//
		// };
		// if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
		// int orientation = intent.getIntExtra(
		// MediaStore.EXTRA_SCREEN_ORIENTATION,
		// ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		// if (orientation != getRequestedOrientation()) {
		// setRequestedOrientation(orientation);
		// }
		// }
		// mFinishOnCompletion = intent.getBooleanExtra(
		// MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
		// Window win = getWindow();
		// WindowManager.LayoutParams winParams = win.getAttributes();
		// winParams.buttonBrightness =
		// WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
		// win.setAttributes(winParams);
	}

	protected void setFixedSize(int width, int height) {
		if (mControl != null) {
			mControl.setFixedSize(width, height);
		}
	}

	protected void set2Play() {
		rootView.postDelayed(new Runnable() {
			@Override
			public void run() {
				setPlay();
			}
		}, 600);
	}

	private void setPlay() {
		if (mUri == null) {
			Toast.makeText(this, "不存在该视频地址", 0).show();
			return;
		}
		mControl = new MovieViewControl(rootView, this, mUri) {
			@Override
			public void onCompletion() {
				_this.onCompletion();
				mControl.showControl();
			}

			@Override
			public void onSetButtons(MediaController mediaController) {
				_this.onSetButtons(mediaController);
			}

		};
		mControl.showVideoView();
		mFinishOnCompletion = true;
		Window win = getWindow();
		WindowManager.LayoutParams winParams = win.getAttributes();
		winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
		win.setAttributes(winParams);
	}

	protected int getDuration() {
		return mControl.getDuration();
	}

	@Override
	public void finish() {
		mControl.resetVideoView();
		super.finish();
	}

	@Override
	public void onPause() {
		if (mControl != null) {
			mControl.onPause();
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		if (mControl != null) {
			mControl.onResume();
		}
		super.onResume();
	}

	@Override
	public void onDestroy() {
		if (mControl != null) {
			mControl.onDestroy();
		}
		_this = null;
		super.onDestroy();
	}

	protected void onCompletion() {
	}

	protected void onSetButtons(MediaController mediaController) {
		int margin = 5;
		FrameLayout.LayoutParams anewFrameParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		anewFrameParams.gravity = Gravity.LEFT;
		anewFrameParams.setMargins(margin, margin, margin, margin);
		Button anewButton = new Button(mediaController.getContext());
		anewButton.setText("重拍");
		mediaController.addView(anewButton, anewFrameParams);

		FrameLayout.LayoutParams useFrameParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		useFrameParams.gravity = Gravity.RIGHT;
		useFrameParams.setMargins(margin, margin, margin, margin);
		Button useButton = new Button(mediaController.getContext());
		useButton.setText("使用");
		mediaController.addView(useButton, useFrameParams);
	}

	
}
