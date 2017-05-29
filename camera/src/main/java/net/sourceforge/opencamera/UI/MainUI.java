package net.sourceforge.opencamera.UI;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

/** This contains functionality related to the main UI.
 */
public class MainUI {
	private static final String TAG = "MainUI";

	private final MainActivity main_activity;

	private volatile boolean popup_view_is_open; // must be volatile for test project reading the state
    private PopupView popup_view;

    private int current_orientation;
	private boolean ui_placement_right = true;

	private boolean immersive_mode;
    private boolean show_gui = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video

	public MainUI(MainActivity main_activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "MainUI");
		this.main_activity = main_activity;
		
		this.setSeekbarColors();

		this.setIcon(R.id.gallery);
		this.setIcon(R.id.settings);
		this.setIcon(R.id.popup);
		this.setIcon(R.id.switch_video);
		this.setIcon(R.id.switch_camera);
		this.setIcon(R.id.audio_control);
	}
	
	private void setIcon(int id) {
		if( MyDebug.LOG )
			Log.d(TAG, "setIcon: " + id);
	    ImageButton button = (ImageButton)main_activity.findViewById(id);
	    button.setBackgroundColor(Color.argb(63, 63, 63, 63)); // n.b., rgb color seems to be ignored for Android 6 onwards, but still relevant for older versions
	}
	
	private void setSeekbarColors() {
		if( MyDebug.LOG )
			Log.d(TAG, "setSeekbarColors");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			ColorStateList progress_color = ColorStateList.valueOf( Color.argb(255, 240, 240, 240) );
			ColorStateList thumb_color = ColorStateList.valueOf( Color.argb(255, 255, 255, 255) );

			SeekBar seekBar = (SeekBar)main_activity.findViewById(R.id.focus_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);
		}
	}

	/** Similar view.setRotation(ui_rotation), but achieves this via an animation.
	 */
	private void setViewRotation(View view, float ui_rotation) {
		//view.setRotation(ui_rotation);
		float rotate_by = ui_rotation - view.getRotation();
		if( rotate_by > 181.0f )
			rotate_by -= 360.0f;
		else if( rotate_by < -181.0f )
			rotate_by += 360.0f;
		// view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
		// we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
		view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
	}

    public void layoutUI() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI");
			debug_time = System.currentTimeMillis();
		}
		//main_activity.getPreview().updateUIPlacement();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		String ui_placement = sharedPreferences.getString(PreferenceKeys.getUIPlacementPreferenceKey(), "ui_right");
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		this.ui_placement_right = ui_placement.equals("ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		// new code for orientation fixed to landscape	
		// the display orientation should be locked to landscape, but how many degrees is that?
	    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
    		default:
    			break;
	    }
	    // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
	    // relative_orientation is clockwise from landscape-left
    	//int relative_orientation = (current_orientation + 360 - degrees) % 360;
    	int relative_orientation = (current_orientation + degrees) % 360;
		if( MyDebug.LOG ) {
			Log.d(TAG, "    current_orientation = " + current_orientation);
			Log.d(TAG, "    degrees = " + degrees);
			Log.d(TAG, "    relative_orientation = " + relative_orientation);
		}
		int ui_rotation = (360 - relative_orientation) % 360;
		main_activity.getPreview().setUIRotation(ui_rotation);
		int align_left = RelativeLayout.ALIGN_LEFT;
		int align_right = RelativeLayout.ALIGN_RIGHT;
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int above = RelativeLayout.ABOVE;
		int below = RelativeLayout.BELOW;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( !ui_placement_right ) {
			above = RelativeLayout.BELOW;
			below = RelativeLayout.ABOVE;
			align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
			align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
		}

		{
			// we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
			View view = main_activity.findViewById(R.id.gui_anchor);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gui_anchor);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.settings);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gallery);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.popup);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.settings);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
			if( !main_activity.isPhoto() || !main_activity.isVideo() ){
				view.setVisibility(View.GONE);
			}
	
			view = main_activity.findViewById(R.id.switch_camera);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_video);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.audio_control);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_camera);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.pause_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.focus_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(left_of, R.id.pause_video);
			layoutParams.addRule(right_of, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
		}

		{
			View view = main_activity.findViewById(R.id.popup_container);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			//layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(align_right, R.id.popup);
			layoutParams.addRule(below, R.id.popup);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(align_parent_top, 0);
			view.setLayoutParams(layoutParams);

			setViewRotation(view, ui_rotation);
			// reset:
			view.setTranslationX(0.0f);
			view.setTranslationY(0.0f);
			if( MyDebug.LOG ) {
				Log.d(TAG, "popup view width: " + view.getWidth());
				Log.d(TAG, "popup view height: " + view.getHeight());
			}
			if( ui_rotation == 0 || ui_rotation == 180 ) {
				view.setPivotX(view.getWidth()/2.0f);
				view.setPivotY(view.getHeight()/2.0f);
			}
			else {
				view.setPivotX(view.getWidth());
				view.setPivotY(ui_placement_right ? 0.0f : view.getHeight());
				if( ui_placement_right ) {
					if( ui_rotation == 90 )
						view.setTranslationY( view.getWidth() );
					else if( ui_rotation == 270 )
						view.setTranslationX( - view.getHeight() );
				}
				else {
					if( ui_rotation == 90 )
						view.setTranslationX( - view.getHeight() );
					else if( ui_rotation == 270 )
						view.setTranslationY( - view.getWidth() );
				}
			}
		}

		setTakePhotoIcon();
		// no need to call setSwitchCameraContentDescription()

		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI: total time: " + (System.currentTimeMillis() - debug_time));
		}
    }

    /** Set icon for taking photos vs videos.
	 *  Also handles content descriptions for the take photo button and switch video button.
     */
    public void setTakePhotoIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "setTakePhotoIcon()");
		if( main_activity.getPreview() != null ) {
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
			int resource;
			int content_description;
			int switch_video_content_description;
			if( main_activity.getPreview().isVideo() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "set icon to video");
				resource = main_activity.getPreview().isVideoRecording() ? R.drawable.take_video_recording : R.drawable.take_video_selector;
				content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;
				switch_video_content_description = R.string.switch_to_photo;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "set icon to photo");
				resource = R.drawable.take_photo_selector;
				content_description = R.string.take_photo;
				switch_video_content_description = R.string.switch_to_video;
			}
			view.setImageResource(resource);
			view.setContentDescription( main_activity.getResources().getString(content_description) );
			view.setTag(resource); // for testing

			view = (ImageButton)main_activity.findViewById(R.id.switch_video);
			view.setContentDescription( main_activity.getResources().getString(switch_video_content_description) );
		}
    }

    /** Set content description for switch camera button.
     */
    public void setSwitchCameraContentDescription() {
		if( MyDebug.LOG )
			Log.d(TAG, "setSwitchCameraContentDescription()");
		if( main_activity.getPreview() != null && main_activity.getPreview().canSwitchCamera() ) {
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.switch_camera);
			int content_description;
			int cameraId = main_activity.getNextCameraId();
		    if( main_activity.getPreview().getCameraControllerManager().isFrontFacing( cameraId ) ) {
				content_description = R.string.switch_to_front_camera;
		    }
		    else {
				content_description = R.string.switch_to_back_camera;
		    }
			if( MyDebug.LOG )
				Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
			view.setContentDescription( main_activity.getResources().getString(content_description) );
		}
    }

	/** Set content description for pause video button.
	 */
	public void setPauseVideoContentDescription() {
		if (MyDebug.LOG)
			Log.d(TAG, "setPauseVideoContentDescription()");
		View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
		int content_description;
		if( main_activity.getPreview().isVideoRecordingPaused() ) {
			content_description = R.string.resume_video;
		}
		else {
			content_description = R.string.pause_video;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
		pauseVideoButton.setContentDescription(main_activity.getResources().getString(content_description));
	}

    public boolean getUIPlacementRight() {
    	return this.ui_placement_right;
    }

    public void onOrientationChanged(int orientation) {
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
		    orientation = (orientation + 45) / 90 * 90;
		    orientation = orientation % 360;
		    if( orientation != current_orientation ) {
			    this.current_orientation = orientation;
				if( MyDebug.LOG ) {
					Log.d(TAG, "current_orientation is now: " + current_orientation);
				}
			    layoutUI();
			}
		}
	}

    public void setImmersiveMode(final boolean immersive_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "setImmersiveMode: " + immersive_mode);
    	this.immersive_mode = immersive_mode;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				// if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
		    	//final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
		    	final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
				if( MyDebug.LOG )
					Log.d(TAG, "setImmersiveMode: set visibility: " + visibility);
		    	// n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
			    View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = main_activity.findViewById(R.id.switch_video);
			    View audioControlButton = main_activity.findViewById(R.id.audio_control);
			    View popupButton = main_activity.findViewById(R.id.popup);
			    View galleryButton = main_activity.findViewById(R.id.gallery);
			    View settingsButton = main_activity.findViewById(R.id.settings);
			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
			    	switchCameraButton.setVisibility(visibility);

				if( !main_activity.isVideo() || !main_activity.isPhoto() )
		    		switchVideoButton.setVisibility(View.GONE);
				else
					switchVideoButton.setVisibility(visibility);

			    if( main_activity.hasAudioControl() )
			    	audioControlButton.setVisibility(visibility);
		    	popupButton.setVisibility(visibility);
			    galleryButton.setVisibility(visibility);
			    settingsButton.setVisibility(visibility);
				if( MyDebug.LOG ) {
					Log.d(TAG, "has_zoom: " + main_activity.getPreview().supportsZoom());
				}

				if( !immersive_mode ) {
					// make sure the GUI is set up as expected
					showGUI(show_gui);
				}
			}
		});
    }
    
    public boolean inImmersiveMode() {
    	return immersive_mode;
    }

    public void showGUI(final boolean show) {
		if( MyDebug.LOG )
			Log.d(TAG, "showGUI: " + show);
		this.show_gui = show;
		if( inImmersiveMode() )
			return;
		if( show && main_activity.usingKitKatImmersiveMode() ) {
			// call to reset the timer
			main_activity.initImmersiveMode();
		}
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
		    	final int visibility = show ? View.VISIBLE : View.GONE;
			    View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = main_activity.findViewById(R.id.switch_video);
			    View audioControlButton = main_activity.findViewById(R.id.audio_control);
			    View popupButton = main_activity.findViewById(R.id.popup);
			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
			    	switchCameraButton.setVisibility(visibility);

				if( !main_activity.isPhoto() || !main_activity.isVideo())
					switchVideoButton.setVisibility(View.GONE);
			    else if( !main_activity.getPreview().isVideo() )
			    	switchVideoButton.setVisibility(visibility); // still allow switch video when recording video
			    if( main_activity.hasAudioControl() )
			    	audioControlButton.setVisibility(visibility);
			    if( !show ) {
			    	closePopup(); // we still allow the popup when recording video, but need to update the UI (so it only shows flash options), so easiest to just close
			    }
			    if( !main_activity.getPreview().isVideo() || !main_activity.getPreview().supportsFlash() )
			    	popupButton.setVisibility(visibility); // still allow popup in order to change flash mode when recording video
			}
		});
    }

    public void audioControlStarted() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.audio_control);
		view.setImageResource(R.drawable.ic_mic_red_48dp);
		view.setContentDescription( main_activity.getResources().getString(R.string.audio_control_stop) );
    }

    public void audioControlStopped() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.audio_control);
		view.setImageResource(R.drawable.ic_mic_white_48dp);
		view.setContentDescription( main_activity.getResources().getString(R.string.audio_control_start) );
    }

    public void setPopupIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPopupIcon");
		ImageButton popup = (ImageButton)main_activity.findViewById(R.id.popup);
		String flash_value = main_activity.getPreview().getCurrentFlashValue();
		if( MyDebug.LOG )
			Log.d(TAG, "flash_value: " + flash_value);
    	if( flash_value != null && flash_value.equals("flash_off") ) {
    		popup.setImageResource(R.drawable.popup_flash_off);
    	}
    	else if( flash_value != null && flash_value.equals("flash_torch") ) {
    		popup.setImageResource(R.drawable.popup_flash_torch);
    	}
		else if( flash_value != null && ( flash_value.equals("flash_auto") || flash_value.equals("flash_frontscreen_auto") ) ) {
    		popup.setImageResource(R.drawable.popup_flash_auto);
    	}
		else if( flash_value != null && ( flash_value.equals("flash_on") || flash_value.equals("flash_frontscreen_on") ) ) {
    		popup.setImageResource(R.drawable.popup_flash_on);
    	}
    	else if( flash_value != null && flash_value.equals("flash_red_eye") ) {
    		popup.setImageResource(R.drawable.popup_flash_red_eye);
    	}
    	else {
    		popup.setImageResource(R.drawable.popup);
    	}
    }

    public void closePopup() {
		if( MyDebug.LOG )
			Log.d(TAG, "close popup");
		if( popupIsOpen() ) {
			ViewGroup popup_container = (ViewGroup)main_activity.findViewById(R.id.popup_container);
			popup_container.removeAllViews();
			popup_view_is_open = false;
			/* Not destroying the popup doesn't really gain any performance.
			 * Also there are still outstanding bugs to fix if we wanted to do this:
			 *   - Not resetting the popup menu when switching between photo and video mode. See test testVideoPopup().
			 *   - When changing options like flash/focus, the new option isn't selected when reopening the popup menu. See test
			 *     testPopup().
			 *   - Changing settings potentially means we have to recreate the popup, so the natural place to do this is in
			 *     MainActivity.updateForSettings(), but doing so makes the popup close when checking photo or video resolutions!
			 *     See test testSwitchResolution().
			 */
			destroyPopup();
			main_activity.initImmersiveMode(); // to reset the timer when closing the popup
		}
    }

    public boolean popupIsOpen() {
    	return popup_view_is_open;
    }
    
    public void destroyPopup() {
		if( popupIsOpen() ) {
			closePopup();
		}
		popup_view = null;
    }

    public void togglePopupSettings() {
		final ViewGroup popup_container = (ViewGroup)main_activity.findViewById(R.id.popup_container);
		if( popupIsOpen() ) {
			closePopup();
			return;
		}
		if( main_activity.getPreview().getCameraController() == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "open popup");

		main_activity.getPreview().cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
		main_activity.stopAudioListeners();

    	final long time_s = System.currentTimeMillis();

    	{
			// prevent popup being transparent
			popup_container.setBackgroundColor(Color.BLACK);
			popup_container.setAlpha(0.9f);
		}

    	if( popup_view == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "create new popup_view");
    		popup_view = new PopupView(main_activity);
    	}
    	else {
			if( MyDebug.LOG )
				Log.d(TAG, "use cached popup_view");
    	}
		popup_container.addView(popup_view);
		popup_view_is_open = true;
		
        // need to call layoutUI to make sure the new popup is oriented correctly
		// but need to do after the layout has been done, so we have a valid width/height to use
		// n.b., even though we only need the portion of layoutUI for the popup container, there
		// doesn't seem to be any performance benefit in only calling that part
		popup_container.getViewTreeObserver().addOnGlobalLayoutListener( 
			new OnGlobalLayoutListener() {
				@SuppressWarnings("deprecation")
				@Override
			    public void onGlobalLayout() {
					if( MyDebug.LOG )
						Log.d(TAG, "onGlobalLayout()");
					if( MyDebug.LOG )
						Log.d(TAG, "time after global layout: " + (System.currentTimeMillis() - time_s));
					layoutUI();
					if( MyDebug.LOG )
						Log.d(TAG, "time after layoutUI: " + (System.currentTimeMillis() - time_s));
		    		// stop listening - only want to call this once!
		            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
		            	popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		            } else {
		            	popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		            }

		    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		    		String ui_placement = sharedPreferences.getString(PreferenceKeys.getUIPlacementPreferenceKey(), "ui_right");
		    		boolean ui_placement_right = ui_placement.equals("ui_right");
		            ScaleAnimation animation = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, ui_placement_right ? 0.0f : 1.0f);
		    		animation.setDuration(100);
		    		popup_container.setAnimation(animation);
		        }
			}
		);

		if( MyDebug.LOG )
			Log.d(TAG, "time to create popup: " + (System.currentTimeMillis() - time_s));
    }

	@SuppressWarnings("deprecation")
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyDown: " + keyCode);
		switch( keyCode ) {
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS: // media codes are for "selfie sticks" buttons
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_MEDIA_STOP:
			{
				//TODO Selfie sticks ?
				break;
			}
			case KeyEvent.KEYCODE_MENU:
			{
				// needed to support hardware menu button
				// tested successfully on Samsung S3 (via RTL)
				// see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
				main_activity.openSettings();
				return true;
			}
			case KeyEvent.KEYCODE_CAMERA:
			{
				if( event.getRepeatCount() == 0 ) {
					main_activity.takePicture();
					return true;
				}
			}
			case KeyEvent.KEYCODE_FOCUS:
			{
				// important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel - causes problem with hardware camera key where a half-press means to focus
				// also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down - see https://sourceforge.net/p/opencamera/tickets/174/ ,
				// or same issue above for volume key focus
				if( event.getDownTime() == event.getEventTime() && !main_activity.getPreview().isFocusWaiting() ) {
					if( MyDebug.LOG )
						Log.d(TAG, "request focus due to focus key");
					main_activity.getPreview().requestAutoFocus();
				}
				return true;
			}
			case KeyEvent.KEYCODE_ZOOM_IN:
			{
				return true;
			}
			case KeyEvent.KEYCODE_ZOOM_OUT:
			{
				return true;
			}
		}
		return false;
	}

	public void onKeyUp(int keyCode, KeyEvent event) {
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyUp: " + keyCode);
	}

    // for testing
    public View getPopupButton(String key) {
    	return popup_view.getPopupButton(key);
    }
}
