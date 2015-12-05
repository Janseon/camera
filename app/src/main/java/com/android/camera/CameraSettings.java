/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.camera.R;


/**
 * Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
	private static final int NOT_FOUND = -1;

	public static final String CAMERA_IMAGE_BUCKET_NAME = Environment
			.getExternalStorageDirectory().toString() + "/DCIM/Camera";

	public static final String KEY_VERSION = "pref_version_key";
	public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
	public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
	public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
	public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
	public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
	public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
	public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
	public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
	public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
	public static final String KEY_QUICK_CAPTURE = "pref_camera_quickcapture_key";
	public static final String KEY_EXPOSURE = "pref_camera_exposure_key";

	public static final String QUICK_CAPTURE_ON = "on";
	public static final String QUICK_CAPTURE_OFF = "off";

	private static final String VIDEO_QUALITY_HIGH = "high";
	private static final String VIDEO_QUALITY_MMS = "mms";
	private static final String VIDEO_QUALITY_YOUTUBE = "youtube";

	public static final String EXPOSURE_DEFAULT_VALUE = "0";

	public static final int CURRENT_VERSION = 4;

	// max video duration in seconds for mms and youtube.
	private static final int MMS_VIDEO_DURATION = 10;
	private static final int YOUTUBE_VIDEO_DURATION = 10 * 60; // 10 mins
	private static final int DEFAULT_VIDEO_DURATION = 30 * 60; // 10 mins

	public static final String DEFAULT_VIDEO_QUALITY_VALUE = "high";

	// MMS video length
	public static final int DEFAULT_VIDEO_DURATION_VALUE = -1;

	@SuppressWarnings("unused")
	private static final String TAG = "CameraSettings";

	private final Context mContext;
	private final Parameters mParameters;

	public CameraSettings(Activity activity, Parameters parameters) {
		mContext = activity;
		mParameters = parameters;
	}

	public static boolean setCameraPictureSize(String candidate,
			List<Size> supported, Parameters parameters) {
		int index = candidate.indexOf('x');
		if (index == NOT_FOUND)
			return false;
		int width = Integer.parseInt(candidate.substring(0, index));
		int height = Integer.parseInt(candidate.substring(index + 1));
		for (Size size : supported) {
			if (size.width == width && size.height == height) {
				parameters.setPictureSize(width, height);
				return true;
			}
		}
		return false;
	}

	public static void initialCameraPictureSize(Context context,
			Parameters parameters) {
		// When launching the camera app first time, we will set the picture
		// size to the first one in the list defined in "arrays.xml" and is also
		// supported by the driver.
		List<Size> supported = parameters.getSupportedPictureSizes();
		if (supported == null)
			return;
		for (String candidate : context.getResources().getStringArray(
				R.array.pref_camera_picturesize_entryvalues)) {
			if (setCameraPictureSize(candidate, supported, parameters)) {
				SharedPreferences.Editor editor = PreferenceManager
						.getDefaultSharedPreferences(context).edit();
				editor.putString(KEY_PICTURE_SIZE, candidate);
				editor.commit();
				return;
			}
		}
		Log.e(TAG, "No supported picture size found");
	}

	private static List<String> sizeListToStringList(List<Size> sizes) {
		ArrayList<String> list = new ArrayList<String>();
		for (Size size : sizes) {
			list.add(String.format("%dx%d", size.width, size.height));
		}
		return list;
	}

	public static boolean getVideoQuality(String quality) {
		return VIDEO_QUALITY_YOUTUBE.equals(quality)
				|| VIDEO_QUALITY_HIGH.equals(quality);
	}

	public static int getVidoeDurationInMillis(String quality) {
		if (VIDEO_QUALITY_MMS.equals(quality)) {
			return MMS_VIDEO_DURATION * 1000;
		} else if (VIDEO_QUALITY_YOUTUBE.equals(quality)) {
			return YOUTUBE_VIDEO_DURATION * 1000;
		}
		return DEFAULT_VIDEO_DURATION * 1000;
	}

	private static boolean checkFsWritable() {
		// Create a temporary file to see whether a volume is really writeable.
		// It's important not to put it in the root directory which may have a
		// limit on the number of files.
		String directoryName = Environment.getExternalStorageDirectory()
				.toString() + "/DCIM";
		File directory = new File(directoryName);
		if (!directory.isDirectory()) {
			if (!directory.mkdirs()) {
				return false;
			}
		}
		return directory.canWrite();
	}

	public static boolean hasStorage() {
		return hasStorage(true);
	}

	public static boolean hasStorage(boolean requireWriteAccess) {
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			if (requireWriteAccess) {
				boolean writable = checkFsWritable();
				return writable;
			} else {
				return true;
			}
		} else if (!requireWriteAccess
				&& Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		}
		return false;
	}
}
