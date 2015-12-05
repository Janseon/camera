package com.android.camera;

import com.camera.R;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

public class MovieViewControl implements MediaPlayer.OnErrorListener,
		MediaPlayer.OnCompletionListener, OnPreparedListener {

	@SuppressWarnings("unused")
	private static final String TAG = "MovieViewControl";

	private static final int HALF_MINUTE = 30 * 1000;
	private static final int TWO_MINUTES = 4 * HALF_MINUTE;

	// Copied from MediaPlaybackService in the Music Player app. Should be
	// public, but isn't.
	private static final String SERVICECMD = "com.android.music.musicservicecommand";
	private static final String CMDNAME = "command";
	private static final String CMDPAUSE = "pause";

	final VideoView mVideoView;
	private final View mProgressView;
	private final Uri mUri;
	private final ContentResolver mContentResolver;

	MediaController mMediaController;

	Handler mHandler = new Handler();

	Runnable mPlayingChecker = new Runnable() {
		public void run() {
			if (mVideoView.isPlaying()) {
				mProgressView.setVisibility(View.GONE);
			} else {
				mHandler.postDelayed(mPlayingChecker, 250);
			}
		}
	};

	public static String formatDuration(final Context context, int durationMs) {
		int duration = durationMs / 1000;
		int h = duration / 3600;
		int m = (duration - h * 3600) / 60;
		int s = duration - (h * 3600 + m * 60);
		String durationValue;
		if (h == 0) {
			durationValue = String.format(
					context.getString(R.string.details_ms), m, s);
		} else {
			durationValue = String.format(
					context.getString(R.string.details_hms), h, m, s);
		}
		return durationValue;
	}

	public MovieViewControl(View rootView, Context context, Uri videoUri) {
		mContentResolver = context.getContentResolver();
		mVideoView = (VideoView) rootView.findViewById(R.id.surface_view);
		mProgressView = rootView.findViewById(R.id.progress_indicator);

		Log.i(TAG, "videoUri=" + videoUri.toString());
		mUri = videoUri;

		// For streams that we expect to be slow to start up, show a
		// progress spinner until playback starts.
		String scheme = mUri.getScheme();
		if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
			mHandler.postDelayed(mPlayingChecker, 250);
		} else {
			mProgressView.setVisibility(View.GONE);
		}

		// mVideoView.getViewTreeObserver().addOnGlobalLayoutListener(this);

		mVideoView.setOnErrorListener(this);
		mVideoView.setOnCompletionListener(this);
		mVideoView.setOnPreparedListener(this);
		mVideoView.setVideoURI(mUri);
		setMediaController();

		// make the video view handle keys for seeking and pausing
		mVideoView.requestFocus();

		Intent i = new Intent(SERVICECMD);
		i.putExtra(CMDNAME, CMDPAUSE);
		context.sendBroadcast(i);

		final Integer bookmark = getBookmark();
		if (bookmark != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(R.string.resume_playing_title);
			builder.setMessage(String.format(
					context.getString(R.string.resume_playing_message),
					formatDuration(context, bookmark)));
			builder.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					onCompletion();
				}
			});
			builder.setPositiveButton(R.string.resume_playing_resume,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mVideoView.seekTo(bookmark);
							mVideoView.start();
						}
					});
			builder.setNegativeButton(R.string.resume_playing_restart,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mVideoView.start();
						}
					});
			builder.show();
		} else {
			// mVideoView.start();
			// mHandler.postDelayed(initPauseRunnable, 100);

			// mVideoView.requestFocus();
			// mVideoView.seekTo(100);
			// mVideoView.start();
			// mVideoView.pause();

			mVideoView.start();
			hideControl();
			mHandler.postDelayed(initPauseRunnable, 500);
		}
	}

	Runnable initPauseRunnable = new Runnable() {
		@Override
		public void run() {
			mVideoView.seekTo(0);
			mVideoView.pause();
			showControl();
		}
	};
	Runnable initPauseRunnable2 = new Runnable() {
		@Override
		public void run() {
			if (mVideoView.isPlaying()) {
				mVideoView.seekTo(0);
				mVideoView.pause();
			} else {
				mHandler.postDelayed(initPauseRunnable2, 100);
			}
		}
	};

	void setFixedSize(int width, int height) {
		mVideoView.getHolder().setFixedSize(width, height);
	}

	private static boolean uriSupportsBookmarks(Uri uri) {
		String scheme = uri.getScheme();
		String authority = uri.getAuthority();
		return ("content".equalsIgnoreCase(scheme) && MediaStore.AUTHORITY
				.equalsIgnoreCase(authority));
	}

	private Integer getBookmark() {
		if (!uriSupportsBookmarks(mUri)) {
			return null;
		}

		String[] projection = new String[] { Video.VideoColumns.DURATION,
				Video.VideoColumns.BOOKMARK };

		try {
			Cursor cursor = mContentResolver.query(mUri, projection, null,
					null, null);
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						int duration = getCursorInteger(cursor, 0);
						int bookmark = getCursorInteger(cursor, 1);
						if ((bookmark < HALF_MINUTE)
								|| (duration < TWO_MINUTES)
								|| (bookmark > (duration - HALF_MINUTE))) {
							return null;
						}
						return Integer.valueOf(bookmark);
					}
				} finally {
					cursor.close();
				}
			}
		} catch (SQLiteException e) {
			// ignore
		}

		return null;
	}

	private static int getCursorInteger(Cursor cursor, int index) {
		try {
			return cursor.getInt(index);
		} catch (SQLiteException e) {
			return 0;
		} catch (NumberFormatException e) {
			return 0;
		}

	}

	private void setBookmark(int bookmark, int duration) {
		if (!uriSupportsBookmarks(mUri)) {
			return;
		}

		ContentValues values = new ContentValues();
		values.put(Video.VideoColumns.BOOKMARK, Integer.toString(bookmark));
		values.put(Video.VideoColumns.DURATION, Integer.toString(duration));
		try {
			mContentResolver.update(mUri, values, null, null);
		} catch (SecurityException ex) {
			// Ignore, can happen if we try to set the bookmark on a read-only
			// resource such as a video attached to GMail.
		} catch (SQLiteException e) {
			// ignore. can happen if the content doesn't support a bookmark
			// column.
		} catch (UnsupportedOperationException e) {
			// ignore. can happen if the external volume is already detached.
		}
	}

	public void onPause() {
		mHandler.removeCallbacksAndMessages(null);
		setBookmark(mVideoView.getCurrentPosition(), mVideoView.getDuration());

		mVideoView.suspend();
	}

	public void onResume() {
		mVideoView.resume();
	}

	public void onDestroy() {
		mVideoView.stopPlayback();
	}

	public boolean onError(MediaPlayer player, int arg1, int arg2) {
		mHandler.removeCallbacksAndMessages(null);
		mProgressView.setVisibility(View.GONE);
		return false;
	}

	public void onCompletion(MediaPlayer mp) {
		onCompletion();
	}

	public void onCompletion() {
	}

	public void onSetButtons(MediaController mediaController) {
	}

	public void setMediaController() {
		mMediaController = new MediaController(mVideoView.getContext()) {
			@Override
			public void setAnchorView(View view) {
				super.setAnchorView(view);
				onSetButtons(this);
			}
		};
		mVideoView.setMediaController(mMediaController);
	}

	public void showControl() {
		if (mMediaController != null) {
			mMediaController.show();
		}
	}

	public void hideControl() {
		if (mMediaController != null) {
			mMediaController.hide();
		}
	}

	public int getDuration() {
		return mVideoView.getDuration();
	}

	void resetVideoView() {
		ViewGroup.LayoutParams layoutParams = mVideoView.getLayoutParams();
		layoutParams.height = 0;
		mVideoView.setLayoutParams(layoutParams);
	}

	void showVideoView() {
		ViewGroup.LayoutParams layoutParams = mVideoView.getLayoutParams();
		layoutParams.width = ViewGroup.LayoutParams.FILL_PARENT;
		layoutParams.height = ViewGroup.LayoutParams.FILL_PARENT;
		mVideoView.setLayoutParams(layoutParams);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		showVideoView();
	}
}
