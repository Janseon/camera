/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.camera.R;

/**
 * The Camcorder activity.
 * 
 * 视频录制的时候，我们有几处角度需要设置，一个是视频预览的时候，功能函数camera.setDisplayOrientation(90)，
 * 这里角度只是影响预览的效果，也就是说如果这里角度不对，预览的图像的角度可能发生倒转， 但录制之后的视频方向是正确的。
 * 另一个是设置摄像机的方向，功能函数mParameters
 * .setRotation(90)，这里设置的角度在拍摄照片的时候会有影响，更多详细解释请阅读：setRotation。
 * 对我们视频录制影响最大的是设置视频的旋转角度
 * ，功能函数mMediaRecorder.setOrientationHint(90)，这里设置的角度将会影响视频播放时图像是否会倒转，
 * 这个方法也仅仅是设置输出视频播放的方向提示
 * ，也就是说并不会在视频录制的时候真正导致源视频帧的旋转，但是如果输出视频的格式是THREE_GPP或MPEG_4,
 * 那么调用这个方法后，会在输出视频加入一个包含旋转角度的合成的矩阵，
 * 根据这个矩阵视频播放器在播放视频的时候可以选择合适的角度，但是，有些视频播放器在播放的时候会选择忽略视频里的这个合成矩阵。
 * 
 */
public class VideoRecorder extends Activity implements View.OnClickListener,
		ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
		MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
		PreviewFrameLayout.OnSizeChangedListener {
	private static final String TAG = "VideoRecorder";

	private static final int INIT_RECORDER = 3;
	private static final int CLEAR_SCREEN_DELAY = 4;
	private static final int UPDATE_RECORD_TIME = 5;
	private static final int ENABLE_SHUTTER_BUTTON = 6;

	private static final int SCREEN_DELAY = 2 * 60 * 1000;
	private static final int MAX_DURATION_MS_DEFAULT = 30 * 1000;

	// mt15i:
	// mProfile.videoFrameWidth=176
	// mProfile.videoFrameHeight=144
	// mProfile.videoBitRate=96000

	private static final float VIDEO_SIZE_RATE = 2.0f;
	private static final int VIDEO_BIT_RATE = 256000 * 3;// mt15i:96000,i9000:256000

	// The brightness settings used when it is set to automatic in the system.
	// The reason why it is set to 0.7 is just because 1.0 is too bright.
	private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

	private static final long NO_STORAGE_ERROR = -1L;
	private static final long CANNOT_STAT_ERROR = -2L;
	private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;

	private static final int STORAGE_STATUS_OK = 0;
	private static final int STORAGE_STATUS_LOW = 1;
	private static final int STORAGE_STATUS_NONE = 2;

	private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

	/**
	 * An unpublished intent flag requesting to start recording straight away
	 * and return as soon as recording is stopped. TODO: consider publishing by
	 * moving into MediaStore.
	 */
	private final static String EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture";

	private SharedPreferences mPreferences;

	private FrameLayout mFrame;
	private PreviewFrameLayout mPreviewFrameLayout;
	private SurfaceView mVideoPreview;
	private SurfaceHolder mSurfaceHolder = null;
	private ImageView mVideoFrame;
	private GLRootView mGLRootView;

	private boolean mQuickCapture;

	private boolean mStartPreviewFail = false;

	private int mStorageStatus = STORAGE_STATUS_OK;

	private MediaRecorder mMediaRecorder;
	private boolean mMediaRecorderRecording = false;
	private long mRecordingStartTime;
	// The video file that the hardware camera is about to record into
	// (or is recording into.)
	private String mCameraVideoFilename;
	private FileDescriptor mCameraVideoFileDescriptor;

	// The video file that has already been recorded, and that is being
	// examined by the user.
	private String mCurrentVideoFilename;
	private Uri mCurrentVideoUri;
	private ContentValues mCurrentVideoValues;

	private CamcorderProfile mProfile;

	// The video duration limit. 0 menas no limit.
	private int mMaxVideoDurationInMs;

	boolean mPausing = false;
	boolean mPreviewing = false; // True if preview is started.

	private ContentResolver mContentResolver;

	private ShutterButton mShutterButton;
	private TextView mRecordingTimeView;
	// private Switcher mSwitcher;
	private boolean mRecordingTimeCountsDown = false;

	private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

	private final Handler mHandler = new MainHandler();
	private Parameters mParameters;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window win = getWindow();

		// Overright the brightness settings if it is automatic
		int mode = Settings.System.getInt(getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS_MODE,
				Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
			WindowManager.LayoutParams winParams = win.getAttributes();
			winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
			win.setAttributes(winParams);
		}

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		readVideoPreferences();

		/*
		 * To reduce startup time, we start the preview in another thread. We
		 * make sure the preview is started at the end of onCreate.
		 */
		Thread startPreviewThread = new Thread(new Runnable() {
			public void run() {
				try {
					mStartPreviewFail = false;
					startPreview();
				} catch (CameraHardwareException e) {
					// In eng build, we throw the exception so that test tool
					// can detect it and report it
					if ("eng".equals(Build.TYPE)) {
						throw new RuntimeException(e);
					}
					mStartPreviewFail = true;
				}
			}
		}, "startPreviewThread");
		startPreviewThread.start();

		mContentResolver = getContentResolver();

		requestWindowFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.video_camera);

		mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame_layout);
		mPreviewFrameLayout.setOnSizeChangedListener(this);
		resizeForPreviewAspectRatio();

		mVideoPreview = (SurfaceView) findViewById(R.id.camera_preview);
		mVideoFrame = (ImageView) findViewById(R.id.video_frame);

		// don't set mSurfaceHolder here. We have it set ONLY within
		// surfaceCreated / surfaceDestroyed, other parts of the code
		// assume that when it is set, the surface is also set.
		SurfaceHolder holder = mVideoPreview.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
		mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
		if (mRecordingTimeView instanceof RotateTextView) {
			((RotateTextView) mRecordingTimeView).setDegree(-90);
		}

		ViewGroup rootView = (ViewGroup) findViewById(R.id.video_camera);
		LayoutInflater inflater = this.getLayoutInflater();
		View controlBar = inflater.inflate(R.layout.camera_control, rootView);
		mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
		mShutterButton.setImageResource(R.drawable.btn_ic_video_record);
		mShutterButton.setOnShutterButtonListener(this);
		mShutterButton.requestFocus();

		((RotateImageView) findViewById(R.id.video_switch_icon)).setDegree(90);

		// Make sure preview is started.
		try {
			startPreviewThread.join();
			if (mStartPreviewFail) {
				showCameraBusyAndFinish();
				return;
			}
		} catch (InterruptedException ex) {
			// ignore
		}
	}

	// This Handler is used to post message back onto the main thread of the
	// application
	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case ENABLE_SHUTTER_BUTTON:
				mShutterButton.setEnabled(true);
				break;

			case CLEAR_SCREEN_DELAY: {
				getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				break;
			}

			case UPDATE_RECORD_TIME: {
				updateRecordingTime();
				break;
			}

			case INIT_RECORDER: {
				initializeRecorder();
				break;
			}

			default:
				Log.v(TAG, "Unhandled message: " + msg.what);
				break;
			}
		}
	}

	private BroadcastReceiver mReceiver = null;

	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
				updateAndShowStorageHint(false);
				stopVideoRecording();
			} else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
				updateAndShowStorageHint(true);
				initializeRecorder();
			} else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
				// SD card unavailable
				// handled in ACTION_MEDIA_EJECT
			} else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
				Toast.makeText(VideoRecorder.this,
						getResources().getString(R.string.wait), 5000).show();
			} else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
				updateAndShowStorageHint(true);
			}
		}
	}

	private String createName(long dateTaken) {
		Date date = new Date(dateTaken);
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				getString(R.string.video_file_name_format));

		return dateFormat.format(date);
	}

	private void showCameraBusyAndFinish() {
		Resources ress = getResources();
		Util.showFatalErrorAndFinish(VideoRecorder.this,
				ress.getString(R.string.camera_error_title),
				ress.getString(R.string.cannot_connect_camera));
	}

	private void changeHeadUpDisplayState() {
		// If the camera resumes behind the lock screen, the orientation
		// will be portrait. That causes OOM when we try to allocation GPU
		// memory for the GLSurfaceView again when the orientation changes. So,
		// we delayed initialization of HeadUpDisplay until the orientation
		// becomes landscape.
		Configuration config = getResources().getConfiguration();
		if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
				&& !mPausing && mGLRootView == null) {
			initializeHeadUpDisplay();
		} else if (mGLRootView != null) {
			finalizeHeadUpDisplay();
		}
	}

	private void initializeHeadUpDisplay() {
		mFrame = (FrameLayout) findViewById(R.id.frame);
		mGLRootView = new GLRootView(this);
		mFrame.addView(mGLRootView);
	}

	private void finalizeHeadUpDisplay() {
		((ViewGroup) mGLRootView.getParent()).removeView(mGLRootView);
		mGLRootView = null;
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	private void startPlayVideoActivity() {
		Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
		try {
			startActivity(intent);
		} catch (android.content.ActivityNotFoundException ex) {
			Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
		}
	}

	protected void startMovieView(String currentVideoFilename,
			Uri currentVideoUri) {
		Intent intent = new Intent(this, MovieView.class);
		// intent.setData(mCurrentVideoUri);
		intent.setData(currentVideoUri);
		try {
			startActivity(intent);
		} catch (android.content.ActivityNotFoundException ex) {
			Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
		}
	}

	public void onClick(View v) {
		if (v == mFrame) {
			discardCurrentVideoAndInitRecorder();
		}
	}

	public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
		// Do nothing (everything happens in onShutterButtonClick).
	}

	private void onStopVideoRecording(boolean valid) {
		// if (mQuickCapture) {
		// stopVideoRecordingAndReturn(valid);
		// } else {
		// stopVideoRecordingAndShowAlert();
		// }

		stopVideoRecording();
		startMovieView(mCurrentVideoFilename, mCurrentVideoUri);
		finish();
	}

	public void onShutterButtonClick(ShutterButton button) {
		if (button == mShutterButton) {
			if (mMediaRecorderRecording) {
				onStopVideoRecording(true);
			} else if (mMediaRecorder != null) {
				// If the click comes before recorder initialization, it is
				// ignored. If users click the button during initialization,
				// the event is put in the queue and record will be started
				// eventually.
				startVideoRecording();
			}
			mShutterButton.setEnabled(false);
			mHandler.sendEmptyMessageDelayed(ENABLE_SHUTTER_BUTTON,
					SHUTTER_BUTTON_TIMEOUT);
		}
	}

	private void discardCurrentVideoAndInitRecorder() {
		deleteCurrentVideo();
		hideAlertAndInitializeRecorder();
	}

	// private OnScreenHint mStorageHint;

	private void updateAndShowStorageHint(boolean mayHaveSd) {
		mStorageStatus = getStorageStatus(mayHaveSd);
		showStorageHint();
	}

	private void showStorageHint() {
		String errorMessage = null;
		switch (mStorageStatus) {
		case STORAGE_STATUS_NONE:
			errorMessage = "getString(R.string.no_storage)";
			break;
		case STORAGE_STATUS_LOW:
			errorMessage = "getString(R.string.spaceIsLow_content)";
		}
		if (errorMessage == null || errorMessage.equals("")) {
			return;
		}
		// Toast.makeText(this, errorMessage, 0).show();
		System.err.println(errorMessage);
	}

	private int getStorageStatus(boolean mayHaveSd) {
		long remaining = mayHaveSd ? getAvailableStorage() : NO_STORAGE_ERROR;
		if (remaining == NO_STORAGE_ERROR) {
			return STORAGE_STATUS_NONE;
		}
		return remaining < LOW_STORAGE_THRESHOLD ? STORAGE_STATUS_LOW
				: STORAGE_STATUS_OK;
	}

	private void readVideoPreferences() {
		String quality = mPreferences.getString(
				CameraSettings.KEY_VIDEO_QUALITY,
				CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);

		boolean videoQualityHigh = CameraSettings.getVideoQuality(quality);

		// Set video quality.
		Intent intent = getIntent();
		if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
			int extraVideoQuality = intent.getIntExtra(
					MediaStore.EXTRA_VIDEO_QUALITY, 0);
			videoQualityHigh = (extraVideoQuality > 0);
		}

		// Set video duration limit. The limit is read from the preference,
		// unless it is specified in the intent.
		if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
			int seconds = intent
					.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
			mMaxVideoDurationInMs = 1000 * seconds;
		} else {
			// mMaxVideoDurationInMs = CameraSettings
			// .getVidoeDurationInMillis(quality);
			mMaxVideoDurationInMs = MAX_DURATION_MS_DEFAULT;
		}
		// videoQualityHigh = true;
		// mProfile = CamcorderProfile
		// .get(videoQualityHigh ? CamcorderProfile.QUALITY_HIGH
		// : CamcorderProfile.QUALITY_LOW);

		videoQualityHigh = false;
		mProfile = CamcorderProfile
				.get(videoQualityHigh ? CamcorderProfile.QUALITY_HIGH
						: CamcorderProfile.QUALITY_LOW);
		mProfile.videoFrameWidth = (int) (mProfile.videoFrameWidth * VIDEO_SIZE_RATE);
		mProfile.videoFrameHeight = (int) (mProfile.videoFrameHeight * VIDEO_SIZE_RATE);
		mProfile.videoBitRate = VIDEO_BIT_RATE;
		Log.i(TAG, "mProfile.videoFrameWidth=" + mProfile.videoFrameWidth);
		Log.i(TAG, "mProfile.videoFrameHeight=" + mProfile.videoFrameHeight);
		Log.i(TAG, "mProfile.videoBitRate=" + mProfile.videoBitRate);
		// mProfile.videoBitRate *= 10;
		// mProfile.videoFrameRate += 3;

		CamcorderProfile highProfile = CamcorderProfile
				.get(CamcorderProfile.QUALITY_HIGH);
		mProfile.videoCodec = highProfile.videoCodec;
		mProfile.audioCodec = highProfile.audioCodec;
		// mProfile.fileFormat = highProfile.fileFormat;
		mProfile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
	}

	private void resizeForPreviewAspectRatio() {
		mPreviewFrameLayout.setAspectRatio((double) mProfile.videoFrameWidth
				/ mProfile.videoFrameHeight);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mPausing = false;

		mVideoPreview.setVisibility(View.VISIBLE);
		readVideoPreferences();
		resizeForPreviewAspectRatio();
		if (!mPreviewing && !mStartPreviewFail) {
			try {
				startPreview();
			} catch (CameraHardwareException e) {
				showCameraBusyAndFinish();
				return;
			}
		}
		keepScreenOnAwhile();

		// install an intent filter to receive SD card related events.
		IntentFilter intentFilter = new IntentFilter(
				Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		intentFilter.addDataScheme("file");
		mReceiver = new MyBroadcastReceiver();
		registerReceiver(mReceiver, intentFilter);
		mStorageStatus = getStorageStatus(true);

		mHandler.postDelayed(new Runnable() {
			public void run() {
				showStorageHint();
			}
		}, 200);

		if (mSurfaceHolder != null) {
			mHandler.sendEmptyMessage(INIT_RECORDER);
		}

		changeHeadUpDisplayState();
	}

	private void setPreviewDisplay(SurfaceHolder holder) {
		try {
			mCameraDevice.setPreviewDisplay(holder);
		} catch (Throwable ex) {
			closeCamera();
			throw new RuntimeException("setPreviewDisplay failed", ex);
		}
	}

	private void startPreview() throws CameraHardwareException {
		Log.v(TAG, "startPreview");
		if (mPreviewing) {
			// After recording a video, preview is not stopped. So just return.
			return;
		}

		if (mCameraDevice == null) {
			// If the activity is paused and resumed, camera device has been
			// released and we need to open the camera.
			mCameraDevice = CameraHolder.instance().open();
		}

		mCameraDevice.lock();
		setCameraParameters();
		setPreviewDisplay(mSurfaceHolder);

		try {
			mCameraDevice.startPreview();
			mPreviewing = true;
		} catch (Throwable ex) {
			closeCamera();
			throw new RuntimeException("startPreview failed", ex);
		}

		// If setPreviewDisplay has been set with a valid surface, unlock now.
		// If surface is null, unlock later. Otherwise, setPreviewDisplay in
		// surfaceChanged will fail.
		if (mSurfaceHolder != null) {
			mCameraDevice.unlock();
		}
	}

	private void closeCamera() {
		Log.v(TAG, "closeCamera");
		if (mCameraDevice == null) {
			Log.d(TAG, "already stopped.");
			return;
		}
		// If we don't lock the camera, release() will fail.
		mCameraDevice.lock();
		CameraHolder.instance().release();
		mCameraDevice = null;
		mPreviewing = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPausing = true;

		changeHeadUpDisplayState();

		// Hide the preview now. Otherwise, the preview may be rotated during
		// onPause and it is annoying to users.
		mVideoPreview.setVisibility(View.INVISIBLE);

		// This is similar to what mShutterButton.performClick() does,
		// but not quite the same.
		if (mMediaRecorderRecording) {
			stopVideoRecording();
			showAlert();
		} else {
			stopVideoRecording();
		}
		closeCamera();

		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		resetScreenOn();
		mHandler.removeMessages(INIT_RECORDER);
	}

	@Override
	public void onUserInteraction() {
		super.onUserInteraction();
		if (!mMediaRecorderRecording)
			keepScreenOnAwhile();
	}

	@Override
	public void onBackPressed() {
		if (mPausing)
			return;
		if (mMediaRecorderRecording) {
			onStopVideoRecording(false);
		}
		// else if (mHeadUpDisplay == null || !mHeadUpDisplay.collapse()) {
		super.onBackPressed();
		// }
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Do not handle any key if the activity is paused.
		if (mPausing) {
			return true;
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_CAMERA:
			if (event.getRepeatCount() == 0) {
				mShutterButton.performClick();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			if (event.getRepeatCount() == 0) {
				mShutterButton.performClick();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_MENU:
			if (mMediaRecorderRecording) {
				onStopVideoRecording(true);
				return true;
			}
			break;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_CAMERA:
			mShutterButton.setPressed(false);
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Make sure we have a surface in the holder before proceeding.
		if (holder.getSurface() == null) {
			Log.d(TAG, "holder.getSurface() == null");
			return;
		}

		if (mPausing) {
			// We're pausing, the screen is off and we already stopped
			// video recording. We don't want to start the camera again
			// in this case in order to conserve power.
			// The fact that surfaceChanged is called _after_ an onPause appears
			// to be legitimate since in that case the lockscreen always returns
			// to portrait orientation possibly triggering the notification.
			return;
		}

		// The mCameraDevice will be null if it is fail to connect to the
		// camera hardware. In this case we will show a dialog and then
		// finish the activity, so it's OK to ignore it.
		if (mCameraDevice == null)
			return;

		if (mMediaRecorderRecording) {
			stopVideoRecording();
		}

		// Set preview display if the surface is being created. Preview was
		// already started.
		if (holder.isCreating()) {
			setPreviewDisplay(holder);
			mCameraDevice.unlock();
			mHandler.sendEmptyMessage(INIT_RECORDER);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		mSurfaceHolder = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		return true;
	}

	private void doReturnToCaller(boolean valid) {
		Intent resultIntent = new Intent();
		int resultCode;
		if (valid) {
			resultCode = RESULT_OK;
			resultIntent.setData(mCurrentVideoUri);
		} else {
			resultCode = RESULT_CANCELED;
		}
		setResult(resultCode, resultIntent);
		finish();
	}

	/**
	 * Returns
	 * 
	 * @return number of bytes available, or an ERROR code.
	 */
	private static long getAvailableStorage() {
		try {
			if (!CameraSettings.hasStorage()) {
				return NO_STORAGE_ERROR;
			} else {
				String storageDirectory = Environment
						.getExternalStorageDirectory().toString();
				StatFs stat = new StatFs(storageDirectory);
				return (long) stat.getAvailableBlocks()
						* (long) stat.getBlockSize();
			}
		} catch (RuntimeException ex) {
			// if we can't stat the filesystem then we don't know how many
			// free bytes exist. It might be zero but just leave it
			// blank since we really don't know.
			return CANNOT_STAT_ERROR;
		}
	}

	private void cleanupEmptyFile() {
		if (mCameraVideoFilename != null) {
			File f = new File(mCameraVideoFilename);
			if (f.length() == 0 && f.delete()) {
				Log.v(TAG, "Empty video file deleted: " + mCameraVideoFilename);
				mCameraVideoFilename = null;
			}
		}
	}

	private android.hardware.Camera mCameraDevice;

	// Prepares media recorder.
	@TargetApi(9)
	private void initializeRecorder() {
		Log.v(TAG, "initializeRecorder");
		if (mMediaRecorder != null)
			return;

		// We will call initializeRecorder() again when the alert is hidden.
		// If the mCameraDevice is null, then this activity is going to finish
		if (isAlertVisible() || mCameraDevice == null)
			return;

		Intent intent = getIntent();
		Bundle myExtras = intent.getExtras();

		long requestedSizeLimit = 0;
		if (myExtras != null) {
			Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
			if (saveUri != null) {
				try {
					mCameraVideoFileDescriptor = mContentResolver
							.openFileDescriptor(saveUri, "rw")
							.getFileDescriptor();
					mCurrentVideoUri = saveUri;
				} catch (java.io.FileNotFoundException ex) {
					// invalid uri
					Log.e(TAG, ex.toString());
				}
			}
			requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
		}
		mMediaRecorder = new MediaRecorder();

		mMediaRecorder.setCamera(mCameraDevice);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mMediaRecorder.setProfile(mProfile);
		mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
		mMediaRecorder.setOrientationHint(90);

		// Set output file.
		if (mStorageStatus != STORAGE_STATUS_OK) {
			mMediaRecorder.setOutputFile("/dev/null");
		} else {
			if (mCameraVideoFileDescriptor != null) {
				mMediaRecorder.setOutputFile(mCameraVideoFileDescriptor);
			} else {
				createVideoPath();
				mMediaRecorder.setOutputFile(mCameraVideoFilename);
			}
		}

		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

		// Set maximum file size.
		// remaining >= LOW_STORAGE_THRESHOLD at this point, reserve a quarter
		// of that to make it more likely that recording can complete
		// successfully.
		long maxFileSize = getAvailableStorage() - LOW_STORAGE_THRESHOLD / 4;
		if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
			maxFileSize = requestedSizeLimit;
		}

		try {
			mMediaRecorder.setMaxFileSize(maxFileSize);
		} catch (RuntimeException exception) {
			// We are going to ignore failure of setMaxFileSize here, as
			// a) The composer selected may simply not support it, or
			// b) The underlying media framework may not handle 64-bit range
			// on the size restriction.
		}

		try {
			mMediaRecorder.prepare();
		} catch (IOException e) {
			Log.e(TAG, "prepare failed for " + mCameraVideoFilename);
			releaseMediaRecorder();
			throw new RuntimeException(e);
		}
		mMediaRecorderRecording = false;
	}

	private void releaseMediaRecorder() {
		Log.v(TAG, "Releasing media recorder.");
		if (mMediaRecorder != null) {
			cleanupEmptyFile();
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
	}

	private void createVideoPath() {
		long dateTaken = System.currentTimeMillis();
		String title = createName(dateTaken);
		String filename = title + ".mp4"; // Used when emailing.
		String cameraDirPath = CameraSettings.CAMERA_IMAGE_BUCKET_NAME;
		String filePath = cameraDirPath + "/" + filename;
		File cameraDir = new File(cameraDirPath);
		cameraDir.mkdirs();
		ContentValues values = new ContentValues(7);
		values.put(Video.Media.TITLE, title);
		values.put(Video.Media.DISPLAY_NAME, filename);
		values.put(Video.Media.DATE_TAKEN, dateTaken);
		values.put(Video.Media.MIME_TYPE, "video/mpeg4");
		values.put(Video.Media.DATA, filePath);
		mCameraVideoFilename = filePath;
		Log.v(TAG, "Current camera video filename: " + mCameraVideoFilename);
		mCurrentVideoValues = values;
	}

	private void registerVideo() {
		if (mCameraVideoFileDescriptor == null) {
			Uri videoTable = Uri.parse("content://media/external/video/media");
			mCurrentVideoValues.put(Video.Media.SIZE, new File(
					mCurrentVideoFilename).length());
			try {
				mCurrentVideoUri = mContentResolver.insert(videoTable,
						mCurrentVideoValues);
			} catch (Exception e) {
				// We failed to insert into the database. This can happen if
				// the SD card is unmounted.
				mCurrentVideoUri = null;
				mCurrentVideoFilename = null;
			} finally {
				Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
			}
		}
		mCurrentVideoValues = null;
	}

	private void deleteCurrentVideo() {
		if (mCurrentVideoFilename != null) {
			deleteVideoFile(mCurrentVideoFilename);
			mCurrentVideoFilename = null;
		}
		if (mCurrentVideoUri != null) {
			mContentResolver.delete(mCurrentVideoUri, null, null);
			mCurrentVideoUri = null;
		}
		updateAndShowStorageHint(true);
	}

	private void deleteVideoFile(String fileName) {
		Log.v(TAG, "Deleting video " + fileName);
		File f = new File(fileName);
		if (!f.delete()) {
			Log.v(TAG, "Could not delete " + fileName);
		}
	}

	// from MediaRecorder.OnErrorListener
	public void onError(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
			// We may have run out of space on the sdcard.
			stopVideoRecording();
			updateAndShowStorageHint(true);
		}
	}

	// from MediaRecorder.OnInfoListener
	public void onInfo(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
			Toast.makeText(VideoRecorder.this,
					R.string.video_reach_duration_limit, Toast.LENGTH_LONG)
					.show();
			if (mMediaRecorderRecording)
				onStopVideoRecording(true);
		} else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
			// Show the toast.
			Toast.makeText(VideoRecorder.this, R.string.video_reach_size_limit,
					Toast.LENGTH_LONG).show();
			if (mMediaRecorderRecording)
				onStopVideoRecording(true);
		}
	}

	/*
	 * Make sure we're not recording music playing in the background, ask the
	 * MediaPlaybackService to pause playback.
	 */
	private void pauseAudioPlayback() {
		// Shamelessly copied from MediaPlaybackService.java, which
		// should be public, but isn't.
		Intent i = new Intent("com.android.music.musicservicecommand");
		i.putExtra("command", "pause");

		sendBroadcast(i);
	}

	private void startVideoRecording() {
		Log.v(TAG, "startVideoRecording");
		if (!mMediaRecorderRecording) {

			if (mStorageStatus != STORAGE_STATUS_OK) {
				Log.v(TAG, "Storage issue, ignore the start request");
				return;
			}

			// Check mMediaRecorder to see whether it is initialized or not.
			if (mMediaRecorder == null) {
				Log.e(TAG, "MediaRecorder is not initialized.");
				return;
			}

			pauseAudioPlayback();

			try {
				mMediaRecorder.setOnErrorListener(this);
				mMediaRecorder.setOnInfoListener(this);
				mMediaRecorder.start(); // Recording is now started
			} catch (RuntimeException e) {
				Log.e(TAG, "Could not start media recorder. ", e);
				return;
			}
			// mHeadUpDisplay.setEnabled(false);

			mMediaRecorderRecording = true;
			mRecordingStartTime = SystemClock.uptimeMillis();
			updateRecordingIndicator(false);
			mRecordingTimeView.setText("");
			mRecordingTimeView.setVisibility(View.VISIBLE);
			updateRecordingTime();
			keepScreenOn();
		}
	}

	private void updateRecordingIndicator(boolean showRecording) {
		int drawableId = showRecording ? R.drawable.btn_ic_video_record
				: R.drawable.btn_ic_video_record_stop;
		Drawable drawable = getResources().getDrawable(drawableId);
		mShutterButton.setImageDrawable(drawable);
	}

	private void stopVideoRecordingAndReturn(boolean valid) {
		stopVideoRecording();
		doReturnToCaller(valid);
	}

	private void stopVideoRecordingAndShowAlert() {
		stopVideoRecording();
		showAlert();
	}

	private void showAlert() {
		fadeOut(findViewById(R.id.shutter_button));
		if (mCurrentVideoFilename != null) {
			mVideoFrame.setImageBitmap(ThumbnailUtils.createVideoThumbnail(
					mCurrentVideoFilename, Video.Thumbnails.MINI_KIND));
			mVideoFrame.setVisibility(View.VISIBLE);
		}
		// int[] pickIds = { R.id.btn_retake, R.id.btn_done, R.id.btn_play };
		// for (int id : pickIds) {
		// View button = findViewById(id);
		// fadeIn(((View) button.getParent()));
		// }
	}

	private void hideAlert() {
		mVideoFrame.setVisibility(View.INVISIBLE);
		fadeIn(findViewById(R.id.shutter_button));
		// int[] pickIds = { R.id.btn_retake, R.id.btn_done, R.id.btn_play };
		// for (int id : pickIds) {
		// View button = findViewById(id);
		// fadeOut(((View) button.getParent()));
		// }
	}

	private static void fadeIn(View view) {
		view.setVisibility(View.VISIBLE);
		Animation animation = new AlphaAnimation(0F, 1F);
		animation.setDuration(500);
		view.startAnimation(animation);
	}

	private static void fadeOut(View view) {
		view.setVisibility(View.INVISIBLE);
		Animation animation = new AlphaAnimation(1F, 0F);
		animation.setDuration(500);
		view.startAnimation(animation);
	}

	private boolean isAlertVisible() {
		return this.mVideoFrame.getVisibility() == View.VISIBLE;
	}

	private void viewLastVideo() {
		Intent intent = null;
		String sizeString = getVideoSizeString(mCurrentVideoFilename);
		String tips = "视频大小为" + sizeString + ",确定要发送吗？";
		Toast.makeText(this, tips, 0).show();
		// if (mThumbController.isUriValid()) {
		// intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
		// try {
		// startActivity(intent);
		// } catch (ActivityNotFoundException ex) {
		// try {
		// intent = new Intent(Intent.ACTION_VIEW,
		// mThumbController.getUri());
		// } catch (ActivityNotFoundException e) {
		// Log.e(TAG, "review video fail", e);
		// }
		// }
		// } else {
		// Log.e(TAG, "Can't view last video.");
		// }
	}

	private void stopVideoRecording() {
		Log.v(TAG, "stopVideoRecording");
		boolean needToRegisterRecording = false;
		if (mMediaRecorderRecording || mMediaRecorder != null) {
			if (mMediaRecorderRecording && mMediaRecorder != null) {
				try {
					mMediaRecorder.setOnErrorListener(null);
					mMediaRecorder.setOnInfoListener(null);
					mMediaRecorder.stop();
				} catch (RuntimeException e) {
					Log.e(TAG, "stop fail: " + e.getMessage());
				}
				// mHeadUpDisplay.setEnabled(true);
				mCurrentVideoFilename = mCameraVideoFilename;
				Log.v(TAG, "Setting current video filename: "
						+ mCurrentVideoFilename);
				needToRegisterRecording = true;
				mMediaRecorderRecording = false;
			}
			releaseMediaRecorder();
			updateRecordingIndicator(true);
			mRecordingTimeView.setVisibility(View.GONE);
			keepScreenOnAwhile();
		}
		if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK) {
			registerVideo();
		}

		mCameraVideoFilename = null;
		mCameraVideoFileDescriptor = null;
	}

	private void resetScreenOn() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void keepScreenOnAwhile() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
	}

	private void keepScreenOn() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void hideAlertAndInitializeRecorder() {
		hideAlert();
		mHandler.sendEmptyMessage(INIT_RECORDER);
	}

	private void acquireVideoThumb() {
		Bitmap videoFrame = ThumbnailUtils.createVideoThumbnail(
				mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
	}

	private void updateRecordingTime() {
		if (!mMediaRecorderRecording) {
			return;
		}
		long now = SystemClock.uptimeMillis();
		long delta = now - mRecordingStartTime;
		long deltaSeconds = delta / 1000;

		// Starting a minute before reaching the max duration
		// limit, we'll countdown the remaining time instead.
		boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0 && delta >= mMaxVideoDurationInMs - 60000);

		long next_update_delay = 1000 - (delta % 1000);
		long seconds;
		if (countdownRemainingTime) {
			delta = Math.max(0, mMaxVideoDurationInMs - delta);
			seconds = (delta + 999) / 1000;
		} else {
			seconds = delta / 1000; // round to nearest
		}

		// String text = seconds2TimeString(deltaSeconds) + "\n"
		// + seconds2TimeString(seconds);

		String text = seconds2TimeString(deltaSeconds);

		mRecordingTimeView.setText(text);

		if (mRecordingTimeCountsDown != countdownRemainingTime) {
			// Avoid setting the color on every update, do it only
			// when it needs changing.
			mRecordingTimeCountsDown = countdownRemainingTime;

			int color = getResources()
					.getColor(
							countdownRemainingTime ? R.color.recording_time_remaining_text
									: R.color.recording_time_elapsed_text);

			mRecordingTimeView.setTextColor(color);
		}

		mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, next_update_delay);
	}

	private String seconds2TimeString(long seconds) {
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long remainderMinutes = minutes - (hours * 60);
		long remainderSeconds = seconds - (minutes * 60);

		String secondsString = Long.toString(remainderSeconds);
		if (secondsString.length() < 2) {
			secondsString = "0" + secondsString;
		}
		String minutesString = Long.toString(remainderMinutes);
		if (minutesString.length() < 2) {
			minutesString = "0" + minutesString;
		}
		String text = minutesString + ":" + secondsString;
		if (hours > 0) {
			String hoursString = Long.toString(hours);
			if (hoursString.length() < 2) {
				hoursString = "0" + hoursString;
			}
			text = hoursString + ":" + text;
		}

		return text;
	}

	private static boolean isSupported(String value, List<String> supported) {
		return supported == null ? false : supported.indexOf(value) >= 0;
	}

	private void setCameraParameters() {
		mParameters = mCameraDevice.getParameters();

		mParameters.setPreviewSize(mProfile.videoFrameWidth,
				mProfile.videoFrameHeight);
		mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

		// Set flash mode.
		String flashMode = mPreferences.getString(
				CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
				getString(R.string.pref_camera_video_flashmode_default));
		List<String> supportedFlash = mParameters.getSupportedFlashModes();
		if (isSupported(flashMode, supportedFlash)) {
			mParameters.setFlashMode(flashMode);
		} else {
			flashMode = mParameters.getFlashMode();
			if (flashMode == null) {
				flashMode = getString(R.string.pref_camera_flashmode_no_flash);
			}
		}

		// Set white balance parameter.
		String whiteBalance = mPreferences.getString(
				CameraSettings.KEY_WHITE_BALANCE,
				getString(R.string.pref_camera_whitebalance_default));
		if (isSupported(whiteBalance, mParameters.getSupportedWhiteBalance())) {
			mParameters.setWhiteBalance(whiteBalance);
		} else {
			whiteBalance = mParameters.getWhiteBalance();
			if (whiteBalance == null) {
				whiteBalance = Parameters.WHITE_BALANCE_AUTO;
			}
		}

		// Set color effect parameter.
		String colorEffect = mPreferences.getString(
				CameraSettings.KEY_COLOR_EFFECT,
				getString(R.string.pref_camera_coloreffect_default));
		if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
			mParameters.setColorEffect(colorEffect);
		}

		mCameraDevice.setParameters(mParameters);
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);

		// If the camera resumes behind the lock screen, the orientation
		// will be portrait. That causes OOM when we try to allocation GPU
		// memory for the GLSurfaceView again when the orientation changes. So,
		// we delayed initialization of HeadUpDisplay until the orientation
		// becomes landscape.
		changeHeadUpDisplayState();
	}

	private void resetCameraParameters() {
		// We need to restart the preview if preview size is changed.
		Size size = mParameters.getPreviewSize();
		if (size.width != mProfile.videoFrameWidth
				|| size.height != mProfile.videoFrameHeight) {
			// It is assumed media recorder is released before
			// onSharedPreferenceChanged, so we can close the camera here.
			closeCamera();
			try {
				resizeForPreviewAspectRatio();
				startPreview(); // Parameters will be set in startPreview().
			} catch (CameraHardwareException e) {
				showCameraBusyAndFinish();
			}
		} else {
			try {
				// We need to lock the camera before writing parameters.
				mCameraDevice.lock();
			} catch (RuntimeException e) {
				// When preferences are added for the first time, this method
				// will be called. But OnScreenSetting is not displayed yet and
				// media recorder still owns the camera. Lock will fail and we
				// just ignore it.
				return;
			}
			setCameraParameters();
			mCameraDevice.unlock();
		}
	}

	public void onSizeChanged() {
	}

	// //////////
	public static String getVideoSizeString(String videoPath) {
		int size = getVideoSize(videoPath);
		DecimalFormat df = new DecimalFormat("###.##");
		float f = ((float) size / (float) (1024 * 1024));
		if (f < 1.0) {
			float f2 = ((float) size / (float) (1024));
			return df.format(new Float(f2).doubleValue()) + "KB";
		} else {
			return df.format(new Float(f).doubleValue()) + "M";
		}
	}

	public static int getVideoSize(String videoPath) {
		File file = new File(videoPath);
		FileInputStream fis;
		try {
			fis = new FileInputStream(file);
			int fileLen = fis.available(); // 这就是文件大小
			return fileLen;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

}
