package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import net.sourceforge.opencamera.Preview.ApplicationInterface;
import net.sourceforge.opencamera.UI.DrawPreview;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";
	private final MainActivity main_activity;
	private final LocationSupplier locationSupplier;
	private final StorageUtils storageUtils;
	private final DrawPreview drawPreview;
	private final ImageSaver imageSaver;

	private TimerTask subtitleVideoTimerTask;

	private final Rect text_bounds = new Rect();
    private boolean used_front_screen_flash ;
	
	/** This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
	 */
	private static class LastImage {
		public final boolean share; // one of the images in the list should have share set to true, to indicate which image to share
		public final String name;
		final Uri uri;

		LastImage(Uri uri, boolean share) {
			this.name = null;
			this.uri = uri;
			this.share = share;
		}
		
		LastImage(String filename, boolean share) {
	    	this.name = filename;
	    	this.uri = Uri.parse("file://" + this.name);
			this.share = share;
		}
	}
	private final List<LastImage> last_images = new ArrayList<>();
	
	// camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
	private int cameraId = 0;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		this.locationSupplier = new LocationSupplier(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
		this.storageUtils = new StorageUtils(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
		this.drawPreview = new DrawPreview(main_activity, this);
		
		this.imageSaver = new ImageSaver(main_activity);
		this.imageSaver.start();
		
        if( savedInstanceState != null ) {
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
        }

		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}
	
	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
    	state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
    	state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
    	state.putFloat("focus_distance", focus_distance);
	}
	
	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( drawPreview != null ) {
			drawPreview.onDestroy();
		}
		if( imageSaver != null ) {
			imageSaver.onDestroy();
		}
	}

	LocationSupplier getLocationSupplier() {
		return locationSupplier;
	}

	StorageUtils getStorageUtils() {
		return storageUtils;
	}
	
	ImageSaver getImageSaver() {
		return imageSaver;
	}

    @Override
	public Context getContext() {
    	return main_activity;
    }
    
    @Override
	public boolean useCamera2() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return main_activity.supportsCamera2() && sharedPreferences.getBoolean(PreferenceKeys.getUseCamera2PreferenceKey(), false);
    }
    
	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}
	
	@Override
	public int createOutputVideoMethod() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	        		return VIDEOMETHOD_URI;
	        	}
	        }
        	// if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
			if( MyDebug.LOG )
				Log.d(TAG, "intent uri not specified");
			// note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
			return VIDEOMETHOD_FILE;
        }
		return VIDEOMETHOD_FILE;
	}

	@Override
	public File createOutputVideoFile() throws IOException {
		return storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "", "mp4", new Date());
	}

	@Override
	public Uri createOutputVideoUri() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	    			return intent_uri;
	        	}
	        }
        }
        throw new RuntimeException(); // programming error if we arrived here
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}
	
    @Override
	public String getFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
    }

    @Override
	public boolean isVideoPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getIsVideoPreferenceKey(), false);
    }

    @Override
	public String getSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getSceneModePreferenceKey(), "auto");
    }

    @Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
    }

	/** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
	 *  photo - in some cases, we may set that to a higher value, then perform processing on the
	 *  resultant JPEG before resaving. This method returns the image quality setting to be used for
	 *  saving the final image (as specified by the user).
     */
	private int getSaveImageQualityPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSaveImageQualityPref");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String image_quality_s = sharedPreferences.getString(PreferenceKeys.getQualityPreferenceKey(), "90");
		int image_quality;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
	}

	@Override
    public int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		// see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
		// at 100% quality for post-processing, the final image will then be saved at the user requested
		// setting
		return getSaveImageQualityPref();
    }
    
	@Override
	public boolean getFaceDetectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getFaceDetectionPreferenceKey(), false);
    }
    
	@Override
	public String getVideoQualityPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), "");
	}
	
    @Override
	public boolean getVideoStabilizationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getVideoStabilizationPreferenceKey(), false);
    }
    
    @Override
    public String getVideoBitratePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoBitratePreferenceKey(), "default");
    }

    @Override
    public String getVideoFPSPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoFPSPreferenceKey(), "default");
    }
    
    @Override
    public long getVideoMaxDurationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_duration_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "0");
		long video_max_duration;
		try {
			video_max_duration = (long)Integer.parseInt(video_max_duration_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_duration value: " + video_max_duration_value);
    		e.printStackTrace();
    		video_max_duration = 0;
        }
		return video_max_duration;
    }

    @Override
    public int getVideoRestartTimesPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String restart_value = sharedPreferences.getString(PreferenceKeys.getVideoRestartPreferenceKey(), "0");
		int remaining_restart_video;
		try {
			remaining_restart_video = Integer.parseInt(restart_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_restart value: " + restart_value);
    		e.printStackTrace();
    		remaining_restart_video = 0;
        }
		return remaining_restart_video;
    }

	long getVideoMaxFileSizeUserPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizeUserPref");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_filesize_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "0");
		long video_max_filesize;
		try {
			video_max_filesize = Integer.parseInt(video_max_filesize_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_filesize value: " + video_max_filesize_value);
    		e.printStackTrace();
    		video_max_filesize = 0;
        }
		if( MyDebug.LOG )
			Log.d(TAG, "video_max_filesize: " + video_max_filesize);
		return video_max_filesize;
	}

	private boolean getVideoRestartMaxFileSizeUserPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoRestartMaxFileSizePreferenceKey(), true);
	}

    @Override
	public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizePref");
		VideoMaxFileSize video_max_filesize = new VideoMaxFileSize();
		video_max_filesize.max_filesize = getVideoMaxFileSizeUserPref();
		video_max_filesize.auto_restart = getVideoRestartMaxFileSizeUserPref();
		
		/* Also if using internal memory without storage access framework, try to set the max filesize so we don't run out of space.
		   This is the only way to avoid the problem where videos become corrupt when run out of space - MediaRecorder doesn't stop on
		   its own, and no error is given!
		   If using SD card, it's not reliable to get the free storage (see https://sourceforge.net/p/opencamera/tickets/153/ ).
		   If using storage access framework, in theory we could check if this was on internal storage, but risk of getting it wrong...
		   so seems safest to leave (the main reason for using SAF is for SD cards, anyway).
		   */

		String folder_name = storageUtils.getSaveLocation();
		if( MyDebug.LOG )
			Log.d(TAG, "saving to: " + folder_name);
		boolean is_internal = false;
		if( !folder_name.startsWith("/") ) {
			is_internal = true;
		}
		else {
			// if save folder path is a full path, see if it matches the "external" storage (which actually means "primary", which typically isn't an SD card these days)
			File storage = Environment.getExternalStorageDirectory();
			if( MyDebug.LOG )
				Log.d(TAG, "compare to: " + storage.getAbsolutePath());
			if( folder_name.startsWith( storage.getAbsolutePath() ) )
				is_internal = true;
		}
		if( is_internal ) {
			if( MyDebug.LOG )
				Log.d(TAG, "using internal storage");
			long free_memory = main_activity.freeMemory() * 1024 * 1024;
			final long min_free_memory = 50000000; // how much free space to leave after video
			// min_free_filesize is the minimum value to set for max file size:
			//   - no point trying to create a really short video
			//   - too short videos can end up being corrupted
			//   - also with auto-restart, if this is too small we'll end up repeatedly restarting and creating shorter and shorter videos
			final long min_free_filesize = 20000000;
			long available_memory = free_memory - min_free_memory;
			if( test_set_available_memory ) {
				available_memory = test_available_memory;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "free_memory: " + free_memory);
				Log.d(TAG, "available_memory: " + available_memory);
			}
			if( available_memory > min_free_filesize ) {
				if( video_max_filesize.max_filesize == 0 || video_max_filesize.max_filesize > available_memory ) {
					video_max_filesize.max_filesize = available_memory;
					// still leave auto_restart set to true - because even if we set a max filesize for running out of storage, the video may still hit a maximum limit before hand, if there's a device max limit set (typically ~2GB)
					if( MyDebug.LOG )
						Log.d(TAG, "set video_max_filesize to avoid running out of space: " + video_max_filesize);
				}
			}
			else {
				if( MyDebug.LOG )
					Log.e(TAG, "not enough free storage to record video");
				throw new NoFreeStorageException();
			}
		}

		
		return video_max_filesize;
	}

    @Override
    public boolean getVideoFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoFlashPreferenceKey(), false);
    }

    @Override
    public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
    }
    
    @Override
    public String getLockOrientationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
    }

    @Override
    public boolean getPausePreviewPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getPausePreviewPreferenceKey(), false);
    }

    @Override
	public boolean getShowToastsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getShowToastsPreferenceKey(), true);
    }

    @Override
    public boolean getShutterSoundPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), true);
    }

    @Override
    public long getTimerPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_timer value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
    public String getRepeatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
    }
    
    @Override
    public long getRepeatIntervalPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getBurstIntervalPreferenceKey(), "0");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
	public boolean getRecordAudioPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getRecordAudioPreferenceKey(), true);
    }
    
    @Override
    public String getRecordAudioChannelsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioChannelsPreferenceKey(), "audio_default");
    }
    
    @Override
    public String getRecordAudioSourcePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioSourcePreferenceKey(), "audio_src_camcorder");
    }

    public boolean getAutoStabilisePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
		if( auto_stabilise && main_activity.supportsAutoStabilise() )
			return true;
		return false;
    }

	@Override
    public int getZoomPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getZoomPref: " + zoom_factor);
    	return zoom_factor;
    }

	@Override
	public double getCalibratedLevelAngle() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getFloat(PreferenceKeys.getCalibratedLevelAnglePreferenceKey(), 0.0f);
	}

	@Override
	public boolean isRawPref() {
    	if( isImageCaptureIntent() )
    		return false;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRawPreferenceKey(), "preference_raw_no").equals("preference_raw_yes");
    }

    @Override
	public boolean useCamera2FakeFlash() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getCamera2FakeFlashPreferenceKey(), false);
	}

	@Override
	public boolean useCamera2FastBurst() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getCamera2FastBurstPreferenceKey(), true);
	}

	@Override
    public boolean isTestAlwaysFocus() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test);
		}
    	return main_activity.is_test;
    }

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
	}


	@Override
	public void touchEvent(MotionEvent event) {
		main_activity.getMainUI().closePopup();
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}
	
	@Override
	public void startingVideo() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getLockVideoPreferenceKey(), false) ) {
			main_activity.lockScreen();
		}
		main_activity.stopAudioListeners(); // important otherwise MediaRecorder will fail to start() if we have an audiolistener! Also don't want to have the speech recognizer going off
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_recording);
		view.setContentDescription( getContext().getResources().getString(R.string.stop_video) );
		view.setTag(R.drawable.take_video_recording); // for testing
	}

	@Override
	public void startedVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "startedVideo()");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
			if( !( main_activity.getMainUI().inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything() ) ) {
				View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
				pauseVideoButton.setVisibility(View.VISIBLE);
			}
			main_activity.getMainUI().setPauseVideoContentDescription();
		}
		this.createOutputVideoMethod();
	}

	@Override
	public void stoppingVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "stoppingVideo()");
		main_activity.unlockScreen();
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void stoppedVideo(final int video_method, final Uri uri, final String filename) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stoppedVideo");
			Log.d(TAG, "video_method " + video_method);
			Log.d(TAG, "uri " + uri);
			Log.d(TAG, "filename " + filename);
		}
		View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
		pauseVideoButton.setVisibility(View.INVISIBLE);
		main_activity.getMainUI().setPauseVideoContentDescription(); // just to be safe
		if( subtitleVideoTimerTask != null ) {
			subtitleVideoTimerTask.cancel();
			subtitleVideoTimerTask = null;
		}

		boolean done = false;
		if( video_method == VIDEOMETHOD_FILE ) {
			if( filename != null ) {
				File file = new File(filename);
				storageUtils.broadcastFile(file, false, true, true);
				done = true;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "done? " + done);

		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( done && video_method == VIDEOMETHOD_FILE ) {
				// do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
			}
		}
		else if( done ) {
			// create thumbnail
			long debug_time = System.currentTimeMillis();
			Bitmap thumbnail = null;
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				if( video_method == VIDEOMETHOD_FILE ) {
					File file = new File(filename);
					retriever.setDataSource(file.getPath());
				}
				else {
					ParcelFileDescriptor pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "r");
					retriever.setDataSource(pfd_saf.getFileDescriptor());
				}
				thumbnail = retriever.getFrameAtTime(-1);
			}
			catch(FileNotFoundException | /*IllegalArgumentException |*/ RuntimeException e) {
				// video file wasn't saved or corrupt video file?
				Log.d(TAG, "failed to find thumbnail");
				e.printStackTrace();
			}
			finally {
				try {
					retriever.release();
				}
				catch(RuntimeException ex) {
					// ignore
				}
			}
			if( thumbnail != null ) {
				ImageButton galleryButton = (ImageButton) main_activity.findViewById(R.id.gallery);
				int width = thumbnail.getWidth();
				int height = thumbnail.getHeight();
				if( MyDebug.LOG )
					Log.d(TAG, "    video thumbnail size " + width + " x " + height);
				if( width > galleryButton.getWidth() ) {
					float scale = (float) galleryButton.getWidth() / width;
					int new_width = Math.round(scale * width);
					int new_height = Math.round(scale * height);
					if( MyDebug.LOG )
						Log.d(TAG, "    scale video thumbnail to " + new_width + " x " + new_height);
					Bitmap scaled_thumbnail = Bitmap.createScaledBitmap(thumbnail, new_width, new_height, true);
					// careful, as scaled_thumbnail is sometimes not a copy!
					if( scaled_thumbnail != thumbnail ) {
						thumbnail.recycle();
						thumbnail = scaled_thumbnail;
					}
				}
				final Bitmap thumbnail_f = thumbnail;
				main_activity.runOnUiThread(new Runnable() {
					public void run() {
						updateThumbnail(thumbnail_f);
					}
				});
			}
			if( MyDebug.LOG )
				Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - debug_time));
		}
	}

	@Override
	public void onVideoInfo(int what, int extra) {
		// we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			if( MyDebug.LOG )
				Log.d(TAG, "max filesize reached");
			int message_id = R.string.video_max_filesize;
			main_activity.getPreview().showToast(null, message_id);
			// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
			// fixed in 1.25; also was correct for 1.23 and earlier
			String debug_value = "info_" + what + "_" + extra;
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("last_video_error", debug_value);
			editor.apply();
		}
	}

	@Override
	public void onFailedStartPreview() {
		main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
	}

	@Override
	public void onCameraError() {
		main_activity.getPreview().showToast(null, R.string.camera_error);
	}

	@Override
	public void onPhotoError() {
	    main_activity.getPreview().showToast(null, R.string.failed_to_take_picture);
	}

	@Override
	public void onVideoError(int what, int extra) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		}
		int message_id = R.string.video_error_unknown;
		if( what == MediaRecorder.MEDIA_ERROR_SERVER_DIED  ) {
			if( MyDebug.LOG )
				Log.d(TAG, "error: server died");
			message_id = R.string.video_error_server_died;
		}
		main_activity.getPreview().showToast(null, message_id);
		// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
		// fixed in 1.25; also was correct for 1.23 and earlier
		String debug_value = "error_" + what + "_" + extra;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("last_video_error", debug_value);
		editor.apply();
	}
	
	@Override
	public void onVideoRecordStartError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStartError");
		String error_message;
		String features = main_activity.getPreview().getErrorFeatures(profile);
		if( features.length() > 0 ) {
			error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		else {
			error_message = getContext().getResources().getString(R.string.failed_to_record_video);
		}
		main_activity.getPreview().showToast(null, error_message);
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void onVideoRecordStopError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStopError");
		//main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
		String features = main_activity.getPreview().getErrorFeatures(profile);
		String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
		if( features.length() > 0 ) {
			error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		main_activity.getPreview().showToast(null, error_message);
	}
	
	@Override
	public void onFailedReconnectError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_reconnect_camera);
	}
	
	@Override
	public void onFailedCreateVideoFileError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

    @Override
	public void hasPausedPreview(boolean paused) {
	    if( paused ) {
	    }
	    else {
		    this.clearLastImages();
	    }
	}
    
    @Override
    public void cameraInOperation(boolean in_operation) {
		if( MyDebug.LOG )
			Log.d(TAG, "cameraInOperation: " + in_operation);
    	if( !in_operation && used_front_screen_flash ) {
    		main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
    		used_front_screen_flash = false;
    	}
    	drawPreview.cameraInOperation(in_operation);
    	main_activity.getMainUI().showGUI(!in_operation);
    }
    
    @Override
    public void turnFrontScreenFlashOn() {
		if( MyDebug.LOG )
			Log.d(TAG, "turnFrontScreenFlashOn");
		used_front_screen_flash = true;
    	main_activity.setBrightnessForCamera(true); // ensure we have max screen brightness, even if user preference not set for max brightness
    	drawPreview.turnFrontScreenFlashOn();
    }

	@Override
	public void onCaptureStarted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onCaptureStarted");
		drawPreview.onCaptureStarted();
	}

    @Override
	public void onPictureCompleted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureCompleted");
		// call this, so that if pause-preview-after-taking-photo option is set, we remove the "taking photo" border indicator straight away
		// also even for normal (not pausing) behaviour, good to remove the border asap
    	drawPreview.cameraInOperation(false);
    }

	@Override
	public void cameraClosed() {
		main_activity.getMainUI().destroyPopup(); // need to close popup - and when camera reopened, it may have different settings
	}
	
	void updateThumbnail(Bitmap thumbnail) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateThumbnail");
		main_activity.updateGalleryIcon(thumbnail);
		drawPreview.updateThumbnail(thumbnail);
		if( this.getPausePreviewPref() ) {
			drawPreview.showLastImage();
		}
	}
	
	@Override
	public void timerBeep(long remaining_time) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "timerBeep()");
			Log.d(TAG, "remaining_time: " + remaining_time);
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "play beep!");
			boolean is_last = remaining_time <= 1000;
			main_activity.playSound(is_last ? R.raw.beep_hi : R.raw.beep);
		}
		if( sharedPreferences.getBoolean(PreferenceKeys.getTimerSpeakPreferenceKey(), false) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "speak countdown!");
			int remaining_time_s = (int)(remaining_time/1000);
			if( remaining_time_s <= 60 )
				main_activity.speak("" + remaining_time_s);
		}
	}

	@Override
	public void layoutUI() {
		main_activity.getMainUI().layoutUI();
	}
	
	@Override
	public void multitouchZoom(int new_zoom) {}

	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

    @Override
    public void setFlashPref(String flash_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
		editor.apply();
    }

    @Override
	public void setVideoPref(boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PreferenceKeys.getIsVideoPreferenceKey(), is_video);
		editor.apply();
    }

    @Override
	public void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
		editor.apply();
    }
    
    @Override
    public void setVideoQualityPref(String video_quality) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), video_quality);
		editor.apply();
    }
    
    @Override
	public void setZoomPref(int zoom) {
		if( MyDebug.LOG )
			Log.d(TAG, "setZoomPref: " + zoom);
    	this.zoom_factor = zoom;
    }
    
    @Override
	public void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		main_activity.requestCameraPermission();
    }
    
    @Override
	public void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		main_activity.requestStoragePermission();
    }
    
    @Override
	public void requestRecordAudioPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestRecordAudioPermission");
		main_activity.requestRecordAudioPermission();
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
    	drawPreview.onDrawPreview(canvas);
    }

	public enum Alignment {
		ALIGNMENT_TOP,
		ALIGNMENT_CENTRE,
		ALIGNMENT_BOTTOM
	}

    public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, Alignment.ALIGNMENT_BOTTOM);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, true);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow) {
		final float scale = getContext().getResources().getDisplayMetrics().density;
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(64);
		int alt_height = 0;
		if( ybounds_text != null ) {
			paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
			alt_height = text_bounds.bottom - text_bounds.top;
		}
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		if( ybounds_text != null ) {
			text_bounds.bottom = text_bounds.top + alt_height;
		}
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
			float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
			if( paint.getTextAlign() == Paint.Align.CENTER )
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
		text_bounds.left += location_x - padding;
		text_bounds.right += location_x + padding;
		// unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
		int top_y_diff = - text_bounds.top + padding - 1;
		if( alignment_y == Alignment.ALIGNMENT_TOP ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			text_bounds.top = location_y - 1;
			text_bounds.bottom = text_bounds.top + height;
			location_y += top_y_diff;
		}
		else if( alignment_y == Alignment.ALIGNMENT_CENTRE ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			int y_diff = - text_bounds.top + padding - 1;
			text_bounds.top = (int)(0.5 * ( (location_y - 1) + (text_bounds.top + location_y - padding) )); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
			text_bounds.bottom = text_bounds.top + height;
			location_y += (int)(0.5*top_y_diff); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
		}
		else {
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;
		}
		if( shadow ) {
			canvas.drawRect(text_bounds, paint);
		}
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}
	
	private boolean saveInBackground(boolean image_capture_intent) {
		boolean do_in_background = true;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( !sharedPreferences.getBoolean(PreferenceKeys.getBackgroundPhotoSavingPreferenceKey(), true) )
			do_in_background = false;
		else if( image_capture_intent )
			do_in_background = false;
		else if( getPausePreviewPref() )
			do_in_background = false;
		return do_in_background;
	}
	
	private boolean isImageCaptureIntent() {
		boolean image_capture_intent = false;
		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			image_capture_intent = true;
		}
		return image_capture_intent;
	}
	
	private boolean saveImage(boolean save_expo, List<byte []> images, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveImage");

		System.gc();

        boolean image_capture_intent = isImageCaptureIntent();
        Uri image_capture_intent_uri = null;
        if( image_capture_intent ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	image_capture_intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
    			if( MyDebug.LOG )
    				Log.d(TAG, "save to: " + image_capture_intent_uri);
	        }
        }

        boolean using_camera2 = main_activity.getPreview().usingCamera2API();
		int image_quality = getSaveImageQualityPref();
		if( MyDebug.LOG )
			Log.d(TAG, "image_quality: " + image_quality);
        boolean do_auto_stabilise = getAutoStabilisePref() && main_activity.getPreview().hasLevelAngle();
		double level_angle = do_auto_stabilise ? main_activity.getPreview().getLevelAngle() : 0.0;
		if( do_auto_stabilise && main_activity.test_have_angle )
			level_angle = main_activity.test_angle;
		if( do_auto_stabilise && main_activity.test_low_memory )
	    	level_angle = 45.0;
		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
		boolean is_front_facing = main_activity.getPreview().getCameraController() != null && main_activity.getPreview().getCameraController().isFrontFacing();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean mirror = is_front_facing && sharedPreferences.getString(PreferenceKeys.getFrontCameraMirrorKey(), "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo");
		boolean store_location = getLocation() != null;
		Location location = store_location ? getLocation() : null;
		boolean store_geo_direction = true;
		double geo_direction = store_geo_direction ? main_activity.getPreview().getGeoDirection() : 0.0;
		boolean has_thumbnail_animation = true;
        
		boolean do_in_background = saveInBackground(image_capture_intent);
		
		int sample_factor = 1;
		if( !this.getPausePreviewPref() ) {
			// if pausing the preview, we use the thumbnail also for the preview, so don't downsample
			// otherwise, we can downsample by 4 to increase performance, without noticeable loss in visual quality (even for the thumbnail animation)
			sample_factor *= 4;
			if( !has_thumbnail_animation ) {
				// can use even lower resolution if we don't have the thumbnail animation
				sample_factor *= 4;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "sample_factor: " + sample_factor);

		boolean success = imageSaver.saveImageJpeg(do_in_background, save_expo, images,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				do_auto_stabilise, level_angle,
				is_front_facing,
				mirror,
				current_date,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);

		if( MyDebug.LOG )
			Log.d(TAG, "saveImage complete, success: " + success);
		
		return success;
	}

    @Override
	public boolean onPictureTaken(byte [] data, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken");

		List<byte []> images = new ArrayList<>();
		images.add(data);

		boolean success = saveImage(false, images, current_date);
        
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken complete, success: " + success);
		
		return success;
	}
    
    @Override
	public boolean onBurstPictureTaken(List<byte []> images, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "onBurstPictureTaken: received " + images.size() + " images");
		return saveImage(true, images, current_date);
    }
    
    void addLastImage(File file, boolean share) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "addLastImage: " + file);
			Log.d(TAG, "share?: " + share);
		}
    	LastImage last_image = new LastImage(file.getAbsolutePath(), share);
    	last_images.add(last_image);
    }

	void clearLastImages() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImages");
		last_images.clear();
		drawPreview.clearLastImage();
	}

	// for testing

	boolean hasThumbnailAnimation() {
		return this.drawPreview.hasThumbnailAnimation();
	}

	
	public boolean test_set_available_memory = false;
	public long test_available_memory = 0;
}
