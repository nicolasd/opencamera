package net.sourceforge.opencamera.UI;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Locale;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;

public class DrawPreview {
	private static final String TAG = "DrawPreview";

	private final MainActivity main_activity;
	private final MyApplicationInterface applicationInterface;

	// store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
	private SharedPreferences sharedPreferences;

	// avoid doing things that allocate memory every frame!
	private final Paint p = new Paint();
	private final RectF face_rect = new RectF();
	private final RectF draw_rect = new RectF();
	private final int [] gui_location = new int[2];
	private final static DecimalFormat decimalFormat = new DecimalFormat("#0.0");
	private final float stroke_width;
	private Calendar calendar;
	private final DateFormat dateFormatTimeInstance = DateFormat.getTimeInstance();

	private float free_memory_gb = -1.0f;
	private long last_free_memory_time;

	private final IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac;
	private float battery_frac;
	private long last_battery_time;

	private final Rect location_dest = new Rect();

	private Bitmap raw_bitmap;
	private Bitmap auto_stabilise_bitmap;
	private Bitmap flash_bitmap;
	private final Rect icon_dest = new Rect();
	private long needs_flash_time = -1; // time when flash symbol comes on (used for fade-in effect)

	private Bitmap last_thumbnail; // thumbnail of last picture taken
	private volatile boolean thumbnail_anim; // whether we are displaying the thumbnail animation; must be volatile for test project reading the state
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
	private final RectF thumbnail_anim_src_rect = new RectF();
	private final RectF thumbnail_anim_dst_rect = new RectF();
	private final Matrix thumbnail_anim_matrix = new Matrix();

	private boolean show_last_image;
	private final RectF last_image_src_rect = new RectF();
	private final RectF last_image_dst_rect = new RectF();
	private final Matrix last_image_matrix = new Matrix();

	private long ae_started_scanning_ms = -1; // time when ae started scanning

    private boolean taking_picture; // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
	private boolean capture_started; // true iff the camera is capturing
    private boolean front_screen_flash; // true iff the front screen display should maximise to simulate flash

	public DrawPreview(MainActivity main_activity, MyApplicationInterface applicationInterface) {
		if( MyDebug.LOG )
			Log.d(TAG, "DrawPreview");
		this.main_activity = main_activity;
		this.applicationInterface = applicationInterface;

		p.setAntiAlias(true);
        p.setStrokeCap(Paint.Cap.ROUND);
		final float scale = getContext().getResources().getDisplayMetrics().density;
		this.stroke_width = (1.0f * scale + 0.5f); // convert dps to pixels
		p.setStrokeWidth(stroke_width);

		raw_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.raw_icon);
		auto_stabilise_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.auto_stabilise_icon);
		flash_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.flash_on);
	}
	
	public void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		// clean up just in case
		if( raw_bitmap != null ) {
			raw_bitmap.recycle();
			raw_bitmap = null;
		}
		if( auto_stabilise_bitmap != null ) {
			auto_stabilise_bitmap.recycle();
			auto_stabilise_bitmap = null;
		}
		if( flash_bitmap != null ) {
			flash_bitmap.recycle();
			flash_bitmap = null;
		}
	}

	private Context getContext() {
    	return main_activity;
    }
	
	public void updateThumbnail(Bitmap thumbnail) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateThumbnail");

		if( MyDebug.LOG )
			Log.d(TAG, "thumbnail_anim started");
		thumbnail_anim = true;
		thumbnail_anim_start_ms = System.currentTimeMillis();

    	Bitmap old_thumbnail = this.last_thumbnail;
    	this.last_thumbnail = thumbnail;
    	if( old_thumbnail != null ) {
    		// only recycle after we've set the new thumbnail
    		old_thumbnail.recycle();
    	}
	}
    
	public boolean hasThumbnailAnimation() {
		return this.thumbnail_anim;
	}
	
	/** Displays the thumbnail as a fullscreen image (used for pause preview option).
	 */
	public void showLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "showLastImage");
		this.show_last_image = true;
	}
	
	public void clearLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImage");
		this.show_last_image = false;
	}

	public void cameraInOperation(boolean in_operation) {
    	if( in_operation && !main_activity.getPreview().isVideo() ) {
    		taking_picture = true;
    	}
    	else {
    		taking_picture = false;
    		front_screen_flash = false;
			capture_started = false;
    	}
    }
	
	public void turnFrontScreenFlashOn() {
		if( MyDebug.LOG )
			Log.d(TAG, "turnFrontScreenFlashOn");
		front_screen_flash = true;
	}

	public void onCaptureStarted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onCaptureStarted");
		capture_started = true;
	}

	private boolean getTakePhotoBorderPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getTakePhotoBorderPreferenceKey(), true);
    }

    private String getTimeStringFromSeconds(long time) {
    	int secs = (int)(time % 60);
    	time /= 60;
    	int mins = (int)(time % 60);
    	time /= 60;
    	long hours = time;
    	return hours + ":" + String.format(Locale.getDefault(), "%02d", mins) + ":" + String.format(Locale.getDefault(), "%02d", secs);
    }

	private void drawGrids(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		String preference_grid = sharedPreferences.getString(PreferenceKeys.getShowGridPreferenceKey(), "preference_grid_none");
		final float scale = getContext().getResources().getDisplayMetrics().density;

		if( camera_controller != null && preference_grid.equals("preference_grid_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/3.0f, 0.0f, canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(2.0f*canvas.getWidth()/3.0f, 0.0f, 2.0f*canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, canvas.getHeight()/3.0f, p);
			canvas.drawLine(0.0f, 2.0f*canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, 2.0f*canvas.getHeight()/3.0f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_phi_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/2.618f, 0.0f, canvas.getWidth()/2.618f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(1.618f*canvas.getWidth()/2.618f, 0.0f, 1.618f*canvas.getWidth()/2.618f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.618f, canvas.getWidth()-1.0f, canvas.getHeight()/2.618f, p);
			canvas.drawLine(0.0f, 1.618f*canvas.getHeight()/2.618f, canvas.getWidth()-1.0f, 1.618f*canvas.getHeight()/2.618f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_4x2") ) {
			p.setColor(Color.GRAY);
			canvas.drawLine(canvas.getWidth()/4.0f, 0.0f, canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(3.0f*canvas.getWidth()/4.0f, 0.0f, 3.0f*canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
			p.setColor(Color.WHITE);
			int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawLine(canvas.getWidth()/2.0f, canvas.getHeight()/2.0f - crosshairs_radius, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f + crosshairs_radius, p);
			canvas.drawLine(canvas.getWidth()/2.0f - crosshairs_radius, canvas.getHeight()/2.0f, canvas.getWidth()/2.0f + crosshairs_radius, canvas.getHeight()/2.0f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_crosshair") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
		}
		else if( camera_controller != null && ( preference_grid.equals("preference_grid_golden_spiral_right") || preference_grid.equals("preference_grid_golden_spiral_left") || preference_grid.equals("preference_grid_golden_spiral_upside_down_right") || preference_grid.equals("preference_grid_golden_spiral_upside_down_left") ) ) {
			canvas.save();
			switch(preference_grid) {
				case "preference_grid_golden_spiral_left":
					canvas.scale(-1.0f, 1.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
					break;
				case "preference_grid_golden_spiral_right":
					// no transformation needed
					break;
				case "preference_grid_golden_spiral_upside_down_left":
					canvas.rotate(180.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
					break;
				case "preference_grid_golden_spiral_upside_down_right":
					canvas.scale(1.0f, -1.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
					break;
			}
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int fibb = 34;
			int fibb_n = 21;
			int left = 0, top = 0;
			int full_width = canvas.getWidth();
			int full_height = canvas.getHeight();
			int width = (int)(full_width*((double)fibb_n)/(double)(fibb));
			int height = full_height;

			for(int count=0;count<2;count++) {
				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left, top, left+2*width, top+2*height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();

				int old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;

				left += width;
				full_width = full_width - width;
				width = full_width;
				height = (int)(height*((double)fibb_n)/(double)(fibb));

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left-width, top, left+width, top+2*height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();

				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;

				top += height;
				full_height = full_height - height;
				height = full_height;
				width = (int)(width*((double)fibb_n)/(double)(fibb));
				left += full_width - width;

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left-width, top-height, left+width, top+height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();

				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;

				full_width = full_width - width;
				width = full_width;
				left -= width;
				height = (int)(height*((double)fibb_n)/(double)(fibb));
				top += full_height - height;

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left, top-height, left+2*width, top+height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();

				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;

				full_height = full_height - height;
				height = full_height;
				top -= height;
				width = (int)(width*((double)fibb_n)/(double)(fibb));
			}

			canvas.restore();
			p.setStyle(Paint.Style.FILL); // reset
		}
		else if( camera_controller != null && ( preference_grid.equals("preference_grid_golden_triangle_1") || preference_grid.equals("preference_grid_golden_triangle_2") ) ) {
			p.setColor(Color.WHITE);
			double theta = Math.atan2(canvas.getWidth(), canvas.getHeight());
			double dist = canvas.getHeight() * Math.cos(theta);
			float dist_x = (float)(dist * Math.sin(theta));
			float dist_y = (float)(dist * Math.cos(theta));
			if( preference_grid.equals("preference_grid_golden_triangle_1") ) {
				canvas.drawLine(0.0f, canvas.getHeight()-1.0f, canvas.getWidth()-1.0f, 0.0f, p);
				canvas.drawLine(0.0f, 0.0f, dist_x, canvas.getHeight()-dist_y, p);
				canvas.drawLine(canvas.getWidth()-1.0f-dist_x, dist_y-1.0f, canvas.getWidth()-1.0f, canvas.getHeight()-1.0f, p);
			}
			else {
				canvas.drawLine(0.0f, 0.0f, canvas.getWidth()-1.0f, canvas.getHeight()-1.0f, p);
				canvas.drawLine(canvas.getWidth()-1.0f, 0.0f, canvas.getWidth()-1.0f-dist_x, canvas.getHeight()-dist_y, p);
				canvas.drawLine(dist_x, dist_y-1.0f, 0.0f, canvas.getHeight()-1.0f, p);
			}
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_diagonals") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(0.0f, 0.0f, canvas.getHeight()-1.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getHeight()-1.0f, 0.0f, 0.0f, canvas.getHeight()-1.0f, p);
			int diff = canvas.getWidth() - canvas.getHeight();
			if( diff > 0 ) {
				canvas.drawLine(diff, 0.0f, diff+canvas.getHeight()-1.0f, canvas.getHeight()-1.0f, p);
				canvas.drawLine(diff+canvas.getHeight()-1.0f, 0.0f, diff, canvas.getHeight()-1.0f, p);
			}
		}
	}

	private void drawCropGuides(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.getShowCropGuidePreferenceKey(), "crop_guide_none");
		if( camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none") ) {
			p.setStyle(Paint.Style.STROKE);
			p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
			double crop_ratio = -1.0;
			switch(preference_crop_guide) {
				case "crop_guide_1":
					crop_ratio = 1.0;
					break;
				case "crop_guide_1.25":
					crop_ratio = 1.25;
					break;
				case "crop_guide_1.33":
					crop_ratio = 1.33333333;
					break;
				case "crop_guide_1.4":
					crop_ratio = 1.4;
					break;
				case "crop_guide_1.5":
					crop_ratio = 1.5;
					break;
				case "crop_guide_1.78":
					crop_ratio = 1.77777778;
					break;
				case "crop_guide_1.85":
					crop_ratio = 1.85;
					break;
				case "crop_guide_2.33":
					crop_ratio = 2.33333333;
					break;
				case "crop_guide_2.35":
					crop_ratio = 2.35006120; // actually 1920:817
					break;
				case "crop_guide_2.4":
					crop_ratio = 2.4;
					break;
			}
			if( crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5 ) {
				int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
				if( crop_ratio > preview.getTargetRatio() ) {
					// crop ratio is wider, so we have to crop top/bottom
					double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
					top = (canvas.getHeight()/2 - (int)new_hheight);
					bottom = (canvas.getHeight()/2 + (int)new_hheight);
				}
				else {
					// crop ratio is taller, so we have to crop left/right
					double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
					left = (canvas.getWidth()/2 - (int)new_hwidth);
					right = (canvas.getWidth()/2 + (int)new_hwidth);
				}
				canvas.drawRect(left, top, right, bottom, p);
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
	}

	private void onDrawInfoLines(Canvas canvas, final int top_y, final int location_size, final String ybounds_text) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		final float scale = getContext().getResources().getDisplayMetrics().density;

		// set up text etc for the multiple lines of "info" (time, free mem, etc)
		p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
		p.setTextAlign(Paint.Align.LEFT);
		int location_x = (int) (50 * scale + 0.5f); // convert dps to pixels
		int location_y = top_y;
		final int diff_y = (int) (16 * scale + 0.5f); // convert dps to pixels
		if( ui_rotation == 90 || ui_rotation == 270 ) {
			int diff = canvas.getWidth() - canvas.getHeight();
			location_x += diff/2;
			location_y -= diff/2;
		}
		if( ui_rotation == 90 ) {
			location_y = canvas.getHeight() - location_y - location_size;
		}
		if( ui_rotation == 180 ) {
			location_x = canvas.getWidth() - location_x;
			p.setTextAlign(Paint.Align.RIGHT);
		}

		if( sharedPreferences.getBoolean(PreferenceKeys.getShowTimePreferenceKey(), true) ) {
			// avoid creating a new calendar object every time
			if( calendar == null )
		        calendar = Calendar.getInstance();
			else
				calendar.setTimeInMillis(System.currentTimeMillis());
	        // n.b., DateFormat.getTimeInstance() ignores user preferences such as 12/24 hour or date format, but this is an Android bug.
	        // Whilst DateUtils.formatDateTime doesn't have that problem, it doesn't print out seconds! See:
	        // http://stackoverflow.com/questions/15981516/simpledateformat-gettimeinstance-ignores-24-hour-format
	        // http://daniel-codes.blogspot.co.uk/2013/06/how-to-correctly-format-datetime.html
	        // http://code.google.com/p/android/issues/detail?id=42104
	        // also possibly related https://code.google.com/p/android/issues/detail?id=181201
	        String current_time = dateFormatTimeInstance.format(calendar.getTime());
	        //String current_time = DateUtils.formatDateTime(getContext(), c.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
	        applicationInterface.drawTextWithBackground(canvas, p, current_time, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);

			if( ui_rotation == 90 ) {
				location_y -= diff_y;
			}
			else {
				location_y += diff_y;
			}
	    }

		if( camera_controller != null && sharedPreferences.getBoolean(PreferenceKeys.getShowFreeMemoryPreferenceKey(), true) ) {
			long time_now = System.currentTimeMillis();
			if( last_free_memory_time == 0 || time_now > last_free_memory_time + 10000 ) {
				// don't call this too often, for UI performance
				long free_mb = main_activity.freeMemory();
				if( free_mb >= 0 ) {
					free_memory_gb = free_mb/1024.0f;
				}
				last_free_memory_time = time_now; // always set this, so that in case of free memory not being available, we aren't calling freeMemory() every frame
			}
			if( free_memory_gb >= 0.0f ) {
				applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.free_memory) + ": " + decimalFormat.format(free_memory_gb) + getContext().getResources().getString(R.string.gb_abbreviation), Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);
			}

			if( ui_rotation == 90 ) {
				location_y -= diff_y;
			}
			else {
				location_y += diff_y;
			}
		}

		if( camera_controller != null && sharedPreferences.getBoolean(PreferenceKeys.getShowISOPreferenceKey(), true) ) {
			String string = "";
			if( camera_controller.captureResultHasIso() ) {
				int iso = camera_controller.captureResultIso();
				if( string.length() > 0 )
					string += " ";
				string += preview.getISOString(iso);
			}
			if( camera_controller.captureResultHasExposureTime() ) {
				long exposure_time = camera_controller.captureResultExposureTime();
				if( string.length() > 0 )
					string += " ";
				string += preview.getExposureTimeString(exposure_time);
			}
			/*if( camera_controller.captureResultHasFrameDuration() ) {
				long frame_duration = camera_controller.captureResultFrameDuration();
				if( string.length() > 0 )
					string += " ";
				string += preview.getFrameDurationString(frame_duration);
			}*/
			if( string.length() > 0 ) {
				boolean is_scanning = false;
				if( camera_controller.captureResultIsAEScanning() ) {
					// only show as scanning if in auto ISO mode (problem on Nexus 6 at least that if we're in manual ISO mode, after pausing and
					// resuming, the camera driver continually reports CONTROL_AE_STATE_SEARCHING)
					String value = sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), main_activity.getPreview().getCameraController().getDefaultISO());
					if( value.equals("auto") ) {
						is_scanning = true;
					}
				}

				int text_color = Color.rgb(255, 235, 59); // Yellow 500
				if( is_scanning ) {
					// we only change the color if ae scanning is at least a certain time, otherwise we get a lot of flickering of the color
					if( ae_started_scanning_ms == -1 ) {
						ae_started_scanning_ms = System.currentTimeMillis();
					}
					else if( System.currentTimeMillis() - ae_started_scanning_ms > 500 ) {
						text_color = Color.rgb(244, 67, 54); // Red 500
					}
				}
				else {
					ae_started_scanning_ms = -1;
				}
				applicationInterface.drawTextWithBackground(canvas, p, string, text_color, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP, ybounds_text, true);

				// only move location_y if we actually print something (because on old camera API, even if the ISO option has
				// been enabled, we'll never be able to display the on-screen ISO)
				if( ui_rotation == 90 ) {
					location_y -= diff_y;
				}
				else {
					location_y += diff_y;
				}
			}
		}

		if( camera_controller != null ) {
			final int symbols_diff_y = (int) (2 * scale + 0.5f); // convert dps to pixels;
			if( ui_rotation == 90 ) {
				location_y -= symbols_diff_y;
			}
			else {
				location_y += symbols_diff_y;
			}
			// padding to align with earlier text
			final int flash_padding = (int) (1 * scale + 0.5f); // convert dps to pixels
			int location_x2 = location_x - flash_padding;
			final int icon_size = (int) (16 * scale + 0.5f); // convert dps to pixels
			if( ui_rotation == 180 ) {
				location_x2 = location_x - icon_size + flash_padding;
			}

			// RAW not enabled in HDR or ExpoBracketing modes (see note in CameraController.takePictureBurstExpoBracketing())
			if( applicationInterface.isRawPref() &&
					!applicationInterface.isVideoPref()  // RAW not relevant for video mode
					 ) {
				icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
				p.setStyle(Paint.Style.FILL);
				p.setColor(Color.BLACK);
				p.setAlpha(64);
				canvas.drawRect(icon_dest, p);
				p.setAlpha(255);
				canvas.drawBitmap(raw_bitmap, null, icon_dest, p);

				if( ui_rotation == 180 ) {
					location_x2 -= icon_size + flash_padding;
				}
				else {
					location_x2 += icon_size + flash_padding;
				}
			}

			if( applicationInterface.getAutoStabilisePref() && !applicationInterface.isVideoPref() ) {
				icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
				p.setStyle(Paint.Style.FILL);
				p.setColor(Color.BLACK);
				p.setAlpha(64);
				canvas.drawRect(icon_dest, p);
				p.setAlpha(255);
				canvas.drawBitmap(auto_stabilise_bitmap, null, icon_dest, p);

				if( ui_rotation == 180 ) {
					location_x2 -= icon_size + flash_padding;
				}
				else {
					location_x2 += icon_size + flash_padding;
				}
			}

			String flash_value = preview.getCurrentFlashValue();
			// note, flash_frontscreen_auto not yet support for the flash symbol (as camera_controller.needsFlash() only returns info on the built-in actual flash, not frontscreen flash)
			if( flash_value != null &&
					( flash_value.equals("flash_on") || flash_value.equals("flash_red_eye") || ( flash_value.equals("flash_auto") && camera_controller.needsFlash() ) ) &&
					!applicationInterface.isVideoPref() ) {
				long time_now = System.currentTimeMillis();
				if( needs_flash_time != -1 ) {
					final long fade_ms = 500;
					float alpha = (time_now - needs_flash_time)/(float)fade_ms;
					if( time_now - needs_flash_time >= fade_ms )
						alpha = 1.0f;
					icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);

					/*if( MyDebug.LOG )
						Log.d(TAG, "alpha: " + alpha);*/
					p.setStyle(Paint.Style.FILL);
					p.setColor(Color.BLACK);
					p.setAlpha((int)(64*alpha));
					canvas.drawRect(icon_dest, p);
					p.setAlpha((int)(255*alpha));
					canvas.drawBitmap(flash_bitmap, null, icon_dest, p);
				}
				else {
					needs_flash_time = time_now;
				}
			}
			else {
				needs_flash_time = -1;
			}
		}
	}

	/** This includes drawing of the UI that requires the canvas to be rotated according to the preview's
	 *  current UI rotation.
	 */
	private void drawUI(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean ui_placement_right = main_activity.getMainUI().getUIPlacementRight();
		final float scale = getContext().getResources().getDisplayMetrics().density;

		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

		int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
		// fine tuning to adjust placement of text with respect to the GUI, depending on orientation
		int text_base_y = 0;
		if( ui_rotation == ( ui_placement_right ? 0 : 180 ) ) {
			text_base_y = canvas.getHeight() - (int)(0.5*text_y);
		}
		else if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
			text_base_y = canvas.getHeight() - (int)(2.5*text_y); // leave room for GUI icons
		}
		else if( ui_rotation == 90 || ui_rotation == 270 ) {
			//text_base_y = canvas.getHeight() + (int)(0.5*text_y);
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
			// align with "top" of the take_photo button, but remember to take the rotation into account!
			view.getLocationOnScreen(gui_location);
			int view_left = gui_location[0];
			preview.getView().getLocationOnScreen(gui_location);
			int this_left = gui_location[0];
			int diff_x = view_left - ( this_left + canvas.getWidth()/2 );
    		/*if( MyDebug.LOG ) {
    			Log.d(TAG, "view left: " + view_left);
    			Log.d(TAG, "this left: " + this_left);
    			Log.d(TAG, "canvas is " + canvas.getWidth() + " x " + canvas.getHeight());
    		}*/
			int max_x = canvas.getWidth();
			if( ui_rotation == 90 ) {
				// so we don't interfere with the top bar info (datetime, free memory, ISO)
				max_x -= (int)(2.5*text_y);
			}
			if( canvas.getWidth()/2 + diff_x > max_x ) {
				// in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio != screen aspect ratio)
				diff_x = max_x - canvas.getWidth()/2;
			}
			text_base_y = canvas.getHeight()/2 + diff_x - (int)(0.5*text_y);
		}
		final int top_y = (int) (5 * scale + 0.5f); // convert dps to pixels
		final int location_size = (int) (20 * scale + 0.5f); // convert dps to pixels

		final String ybounds_text = getContext().getResources().getString(R.string.zoom) + getContext().getResources().getString(R.string.angle) + getContext().getResources().getString(R.string.direction);
		if( camera_controller != null && !preview.isPreviewPaused() ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/

			if( preview.isOnTimer() ) {
				long remaining_time = (preview.getTimerEndTime() - System.currentTimeMillis() + 999)/1000;
				if( MyDebug.LOG )
					Log.d(TAG, "remaining_time: " + remaining_time);
				if( remaining_time > 0 ) {
					p.setTextSize(42 * scale + 0.5f); // convert dps to pixels
					p.setTextAlign(Paint.Align.CENTER);
	            	String time_s;
	            	if( remaining_time < 60 ) {
	            		// simpler to just show seconds when less than a minute
	            		time_s = "" + remaining_time;
	            	}
	            	else {
		            	time_s = getTimeStringFromSeconds(remaining_time);
	            	}
	            	applicationInterface.drawTextWithBackground(canvas, p, time_s, Color.rgb(244, 67, 54), Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() / 2); // Red 500
				}
			}
			else if( preview.isVideoRecording() ) {
            	long video_time = preview.getVideoTime();
            	String time_s = getTimeStringFromSeconds(video_time/1000);
            	/*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
    			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
    			p.setTextAlign(Paint.Align.CENTER);
				int pixels_offset_y = 3*text_y; // avoid overwriting the zoom, and also allow a bit extra space
				int color = Color.rgb(244, 67, 54); // Red 500
            	if( main_activity.isScreenLocked() ) {
            		// writing in reverse order, bottom to top
            		applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_2), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            		applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_1), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            	}
				if( !preview.isVideoRecordingPaused() || ((int)(System.currentTimeMillis() / 500)) % 2 == 0 ) { // if video is paused, then flash the video time
					applicationInterface.drawTextWithBackground(canvas, p, time_s, color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
				}
			}
			else if( taking_picture && capture_started ) {
				if( camera_controller.isManualISO() ) {
					// only show "capturing" text with time for manual exposure time >= 0.5s
					long exposure_time = camera_controller.getExposureTime();
					if( exposure_time >= 500000000L ) {
						if( ((int)(System.currentTimeMillis() / 500)) % 2 == 0 ) {
							p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
							p.setTextAlign(Paint.Align.CENTER);
							int pixels_offset_y = 3*text_y; // avoid overwriting the zoom, and also allow a bit extra space
							int color = Color.rgb(244, 67, 54); // Red 500
							applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.capturing), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
						}
					}
				}
			}
		}
		else if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			if( preview.hasPermissions() ) {
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + pixels_offset, p);
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 2*pixels_offset, p);
			}
			else {
				canvas.drawText(getContext().getResources().getString(R.string.no_permission), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
			}
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}

		if( preview.supportsZoom() && camera_controller != null ) {
			float zoom_ratio = preview.getZoomRatio();
			// only show when actually zoomed in
			if( zoom_ratio > 1.0f + 1.0e-5f ) {
				// Convert the dps to pixels, based on density scale
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				p.setTextAlign(Paint.Align.CENTER);
				applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.zoom) + ": " + zoom_ratio +"x", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, text_base_y - text_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, ybounds_text, true);
			}
		}

		int battery_x = (int) (5 * scale + 0.5f); // convert dps to pixels
		int battery_y = top_y;
		int battery_width = (int) (5 * scale + 0.5f); // convert dps to pixels
		int battery_height = 4*battery_width;
		if( ui_rotation == 90 || ui_rotation == 270 ) {
			int diff = canvas.getWidth() - canvas.getHeight();
			battery_x += diff/2;
			battery_y -= diff/2;
		}
		if( ui_rotation == 90 ) {
			battery_y = canvas.getHeight() - battery_y - battery_height;
		}
		if( ui_rotation == 180 ) {
			battery_x = canvas.getWidth() - battery_x - battery_width;
		}

		//Show Battery
		if( !this.has_battery_frac || System.currentTimeMillis() > this.last_battery_time + 60000 ) {
			// only check periodically - unclear if checking is costly in any way
			// note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
			Intent batteryStatus = main_activity.registerReceiver(null, battery_ifilter);
			int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			has_battery_frac = true;
			battery_frac = battery_level/(float)battery_scale;
			last_battery_time = System.currentTimeMillis();
			if( MyDebug.LOG )
				Log.d(TAG, "Battery status is " + battery_level + " / " + battery_scale + " : " + battery_frac);
		}
		//battery_frac = 0.2999f; // test
		boolean draw_battery = true;
		if( battery_frac <= 0.05f ) {
			// flash icon at this low level
			draw_battery = ((( System.currentTimeMillis() / 1000 )) % 2) == 0;
		}
		if( draw_battery ) {
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			canvas.drawRect(battery_x, battery_y, battery_x+battery_width, battery_y+battery_height, p);
			p.setColor(battery_frac > 0.15f ? Color.rgb(37, 155, 36) : Color.rgb(244, 67, 54)); // Green 500 or Red 500
			p.setStyle(Paint.Style.FILL);
			canvas.drawRect(battery_x+1, battery_y+1+(1.0f-battery_frac)*(battery_height-2), battery_x+battery_width-1, battery_y+battery_height-1, p);
		}

		int location_x = (int) (20 * scale + 0.5f); // convert dps to pixels
		int location_y = top_y;
		if( ui_rotation == 90 || ui_rotation == 270 ) {
			int diff = canvas.getWidth() - canvas.getHeight();
			location_x += diff / 2;
			location_y -= diff / 2;
		}
		if( ui_rotation == 90 ) {
			location_y = canvas.getHeight() - location_y - location_size;
		}
		if( ui_rotation == 180 ) {
			location_x = canvas.getWidth() - location_x - location_size;
		}
		location_dest.set(location_x, location_y, location_x + location_size, location_y + location_size);
		if( applicationInterface.getLocation() != null ) {
			int location_radius = location_size / 10;
			int indicator_x = location_x + location_size;
			int indicator_y = location_y + location_radius / 2 + 1;
			p.setStyle(Paint.Style.FILL);
			p.setColor(applicationInterface.getLocation().getAccuracy() < 25.01f ? Color.rgb(37, 155, 36) : Color.rgb(255, 235, 59)); // Green 500 or Yellow 500
			canvas.drawCircle(indicator_x, indicator_y, location_radius, p);
		}

		onDrawInfoLines(canvas, top_y, location_size, ybounds_text);

		canvas.restore();
	}

	public void onDrawPreview(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onDrawPreview");*/
		// make sure sharedPreferences up to date
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		if( main_activity.getMainUI().inImmersiveMode() ) {
			String immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
			if( immersive_mode.equals("immersive_mode_everything") ) {
				// exit, to ensure we don't display anything!
				return;
			}
		}
		final float scale = getContext().getResources().getDisplayMetrics().density;
		if( camera_controller!= null && front_screen_flash ) {
			p.setColor(Color.WHITE);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		else if( camera_controller != null && taking_picture && getTakePhotoBorderPref() ) {
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			float this_stroke_width = (5.0f * scale + 0.5f); // convert dps to pixels
			p.setStrokeWidth(this_stroke_width);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
			p.setStyle(Paint.Style.FILL); // reset
			p.setStrokeWidth(stroke_width); // reset
		}
		drawGrids(canvas);

		drawCropGuides(canvas);

		if( show_last_image && last_thumbnail != null ) {
			// If changing this code, ensure that pause preview still works when:
			// - Taking a photo in portrait or landscape - and check rotating the device while preview paused
			// - Taking a photo with lock to portrait/landscape options still shows the thumbnail with aspect ratio preserved
			p.setColor(Color.rgb(0, 0, 0)); // in case image doesn't cover the canvas (due to different aspect ratios)
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p); // in case
			last_image_src_rect.left = 0;
			last_image_src_rect.top = 0;
			last_image_src_rect.right = last_thumbnail.getWidth();
			last_image_src_rect.bottom = last_thumbnail.getHeight();
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				last_image_src_rect.right = last_thumbnail.getHeight();
				last_image_src_rect.bottom = last_thumbnail.getWidth();
			}
			last_image_dst_rect.left = 0;
			last_image_dst_rect.top = 0;
			last_image_dst_rect.right = canvas.getWidth();
			last_image_dst_rect.bottom = canvas.getHeight();
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "thumbnail: " + last_thumbnail.getWidth() + " x " + last_thumbnail.getHeight());
				Log.d(TAG, "canvas: " + canvas.getWidth() + " x " + canvas.getHeight());
			}*/
			last_image_matrix.setRectToRect(last_image_src_rect, last_image_dst_rect, Matrix.ScaleToFit.CENTER); // use CENTER to preserve aspect ratio
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				// the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
				float diff = last_thumbnail.getHeight() - last_thumbnail.getWidth();
				last_image_matrix.preTranslate(diff/2.0f, -diff/2.0f);
			}
			last_image_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
			canvas.drawBitmap(last_thumbnail, last_image_matrix, p);
		}
		
		// note, no need to check preferences here, as we do that when setting thumbnail_anim
		if( camera_controller != null && this.thumbnail_anim && last_thumbnail != null ) {
			long time = System.currentTimeMillis() - this.thumbnail_anim_start_ms;
			final long duration = 500;
			if( time > duration ) {
				if( MyDebug.LOG )
					Log.d(TAG, "thumbnail_anim finished");
				this.thumbnail_anim = false;
			}
			else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = last_thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = last_thumbnail.getHeight();
			    View galleryButton = main_activity.findViewById(R.id.gallery);
				float alpha = ((float)time)/(float)duration;

				int st_x = canvas.getWidth()/2;
				int st_y = canvas.getHeight()/2;
				int nd_x = galleryButton.getLeft() + galleryButton.getWidth()/2;
				int nd_y = galleryButton.getTop() + galleryButton.getHeight()/2;
				int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
				int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );

				float st_w = canvas.getWidth();
				float st_h = canvas.getHeight();
				float nd_w = galleryButton.getWidth();
				float nd_h = galleryButton.getHeight();
				//int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				//int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				float correction_w = st_w/nd_w - 1.0f;
				float correction_h = st_h/nd_h - 1.0f;
				int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
				int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
				thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2;
				thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2;
				thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2;
				thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2;
				//canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				//thumbnail_anim_matrix.reset();
				if( ui_rotation == 90 || ui_rotation == 270 ) {
					float ratio = ((float)last_thumbnail.getWidth())/(float)last_thumbnail.getHeight();
					thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				canvas.drawBitmap(last_thumbnail, thumbnail_anim_matrix, p);
			}
		}

		drawUI(canvas);

		if( preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure() ) {
			long time_since_focus_started = preview.timeSinceStartedAutoFocus();
			float min_radius = (40 * scale + 0.5f); // convert dps to pixels
			float max_radius = (45 * scale + 0.5f); // convert dps to pixels
			float radius = min_radius;
			if( time_since_focus_started > 0 ) {
				final long length = 500;
				float frac = ((float)time_since_focus_started) / (float)length;
				if( frac > 1.0f )
					frac = 1.0f;
				if( frac < 0.5f ) {
					float alpha = frac*2.0f;
					radius = (1.0f-alpha) * min_radius + alpha * max_radius;
				}
				else {
					float alpha = (frac-0.5f)*2.0f;
					radius = (1.0f-alpha) * max_radius + alpha * min_radius;
				}
			}
			int size = (int)radius;

			if( preview.isFocusRecentSuccess() )
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			else if( preview.isFocusRecentFailure() )
				p.setColor(Color.rgb(244, 67, 54)); // Red 500
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x;
			int pos_y;
			if( preview.hasFocusArea() ) {
				Pair<Integer, Integer> focus_pos = preview.getFocusPos();
				pos_x = focus_pos.first;
				pos_y = focus_pos.second;
			}
			else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			float frac = 0.5f;
			// horizontal strokes
			canvas.drawLine(pos_x - size, pos_y - size, pos_x - frac*size, pos_y - size, p);
			canvas.drawLine(pos_x + frac*size, pos_y - size, pos_x + size, pos_y - size, p);
			canvas.drawLine(pos_x - size, pos_y + size, pos_x - frac*size, pos_y + size, p);
			canvas.drawLine(pos_x + frac*size, pos_y + size, pos_x + size, pos_y + size, p);
			// vertical strokes
			canvas.drawLine(pos_x - size, pos_y - size, pos_x - size, pos_y - frac*size, p);
			canvas.drawLine(pos_x - size, pos_y + frac*size, pos_x - size, pos_y + size, p);
			canvas.drawLine(pos_x + size, pos_y - size, pos_x + size, pos_y - frac*size, p);
			canvas.drawLine(pos_x + size, pos_y + frac*size, pos_x + size, pos_y + size, p);
			p.setStyle(Paint.Style.FILL); // reset
		}

		CameraController.Face [] faces_detected = preview.getFacesDetected();
		if( faces_detected != null ) {
			p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
			p.setStyle(Paint.Style.STROKE);
			for(CameraController.Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
				if( face.score >= 50 ) {
					face_rect.set(face.rect);
					preview.getCameraToPreviewMatrix().mapRect(face_rect);
					/*int eye_radius = (int) (5 * scale + 0.5f); // convert dps to pixels
					int mouth_radius = (int) (10 * scale + 0.5f); // convert dps to pixels
					float [] top_left = {face.rect.left, face.rect.top};
					float [] bottom_right = {face.rect.right, face.rect.bottom};
					canvas.drawRect(top_left[0], top_left[1], bottom_right[0], bottom_right[1], p);*/
					canvas.drawRect(face_rect, p);
					/*if( face.leftEye != null ) {
						float [] left_point = {face.leftEye.x, face.leftEye.y};
						cameraToPreview(left_point);
						canvas.drawCircle(left_point[0], left_point[1], eye_radius, p);
					}
					if( face.rightEye != null ) {
						float [] right_point = {face.rightEye.x, face.rightEye.y};
						cameraToPreview(right_point);
						canvas.drawCircle(right_point[0], right_point[1], eye_radius, p);
					}
					if( face.mouth != null ) {
						float [] mouth_point = {face.mouth.x, face.mouth.y};
						cameraToPreview(mouth_point);
						canvas.drawCircle(mouth_point[0], mouth_point[1], mouth_radius, p);
					}*/
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
	}
}
