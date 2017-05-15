package net.sourceforge.opencamera.UI;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

/** This defines the UI for the "popup" button, that provides quick access to a
 *  range of options.
 */
public class PopupView extends LinearLayout {
	private static final String TAG = "PopupView";
	public static final float ALPHA_BUTTON_SELECTED = 1.0f;
	public static final float ALPHA_BUTTON = 0.6f;

	private int burst_mode_index = -1;
	private int grid_index = -1;

	private final Map<String, View> popup_buttons = new Hashtable<>();

	public PopupView(Context context) {
		super(context);
		if( MyDebug.LOG )
			Log.d(TAG, "new PopupView: " + this);

		this.setOrientation(LinearLayout.VERTICAL);

		final MainActivity main_activity = (MainActivity)this.getContext();
		final Preview preview = main_activity.getPreview();
		{
	        List<String> supported_flash_values = preview.getSupportedFlashValues();
	    	addButtonOptionsToPopup(supported_flash_values, R.array.flash_icons, R.array.flash_values, getResources().getString(R.string.flash_mode), preview.getCurrentFlashValue(), "TEST_FLASH", new ButtonOptionsPopupListener() {
				@Override
				public void onClick(String option) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked flash: " + option);
					preview.updateFlash(option);
			    	main_activity.getMainUI().setPopupIcon();
					main_activity.closePopup();
				}
			});
		}
    	
		if( preview.isVideo() && preview.isTakingPhoto() ) {
    		// don't add any more options
    	}
    	else {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);

			final String [] burst_mode_values = getResources().getStringArray(R.array.preference_burst_mode_values);
        	String [] burst_mode_entries = getResources().getStringArray(R.array.preference_burst_mode_entries);
    		String burst_mode_value = sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
    		burst_mode_index = Arrays.asList(burst_mode_values).indexOf(burst_mode_value);
    		if( burst_mode_index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!");
				burst_mode_index = 0;
    		}
    		addArrayOptionsToPopup(Arrays.asList(burst_mode_entries), getResources().getString(R.string.preference_burst_mode), true, burst_mode_index, false, "BURST_MODE", new ArrayOptionsPopupListener() {
    			private void update() {
    				if( burst_mode_index == -1 )
    					return;
    				String new_burst_mode_value = burst_mode_values[burst_mode_index];
    				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(PreferenceKeys.getBurstModePreferenceKey(), new_burst_mode_value);
					editor.apply();
    			}
				@Override
				public int onClickPrev() {
	        		if( burst_mode_index != -1 && burst_mode_index > 0 ) {
	        			burst_mode_index--;
	        			update();
	    				return burst_mode_index;
	        		}
					return -1;
				}
				@Override
				public int onClickNext() {
	                if( burst_mode_index != -1 && burst_mode_index < burst_mode_values.length-1 ) {
	                	burst_mode_index++;
	        			update();
	    				return burst_mode_index;
	        		}
					return -1;
				}
    		});

        	final String [] grid_values = getResources().getStringArray(R.array.preference_grid_values);
        	String [] grid_entries = getResources().getStringArray(R.array.preference_grid_entries);
    		String grid_value = sharedPreferences.getString(PreferenceKeys.getShowGridPreferenceKey(), "preference_grid_none");
    		grid_index = Arrays.asList(grid_values).indexOf(grid_value);
    		if( grid_index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't find grid_value " + grid_value + " in grid_values!");
				grid_index = 0;
    		}
    		addArrayOptionsToPopup(Arrays.asList(grid_entries), getResources().getString(R.string.grid), false, grid_index, true, "GRID", new ArrayOptionsPopupListener() {
    			private void update() {
    				if( grid_index == -1 )
    					return;
    				String new_grid_value = grid_values[grid_index];
    				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(PreferenceKeys.getShowGridPreferenceKey(), new_grid_value);
					editor.apply();
    			}
				@Override
				public int onClickPrev() {
	        		if( grid_index != -1 ) {
	        			grid_index--;
	        			if( grid_index < 0 )
	        				grid_index += grid_values.length;
	        			update();
	    				return grid_index;
	        		}
					return -1;
				}
				@Override
				public int onClickNext() {
	                if( grid_index != -1 ) {
	                	grid_index++;
	                	if( grid_index >= grid_values.length )
	                		grid_index -= grid_values.length;
	        			update();
	    				return grid_index;
	        		}
					return -1;
				}
    		});
		}
	}

    private abstract class ButtonOptionsPopupListener {
		public abstract void onClick(String option);
    }
    
    private void addButtonOptionsToPopup(List<String> supported_options, int icons_id, int values_id, String prefix_string, String current_value, String test_key, final ButtonOptionsPopupListener listener) {
		if( MyDebug.LOG )
			Log.d(TAG, "addButtonOptionsToPopup");
    	if( supported_options != null ) {
	    	final long debug_time = System.currentTimeMillis();
        	LinearLayout ll2 = new LinearLayout(this.getContext());
            ll2.setOrientation(LinearLayout.HORIZONTAL);
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 1: " + (System.currentTimeMillis() - debug_time));
        	String [] icons = icons_id != -1 ? getResources().getStringArray(icons_id) : null;
        	String [] values = values_id != -1 ? getResources().getStringArray(values_id) : null;
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 2: " + (System.currentTimeMillis() - debug_time));

			final float scale = getResources().getDisplayMetrics().density;
			int total_width_dp = 280;
			{
				Activity activity = (Activity)this.getContext();
			    Display display = activity.getWindowManager().getDefaultDisplay();
			    DisplayMetrics outMetrics = new DisplayMetrics();
			    display.getMetrics(outMetrics);

			    // the height should limit the width, due to when held in portrait
			    int dpHeight = (int)(outMetrics.heightPixels / scale);
    			if( MyDebug.LOG )
    				Log.d(TAG, "dpHeight: " + dpHeight);
    			dpHeight -= 50; // allow space for the icons at top/right of screen
    			if( total_width_dp > dpHeight )
					total_width_dp = dpHeight;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "total_width_dp: " + total_width_dp);
			final int total_width = (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
			int button_width_dp = total_width_dp/supported_options.size();
			boolean use_scrollview = false;
			if( button_width_dp < 40 ) {
				button_width_dp = 40;
				use_scrollview = true;
			}
			final int button_width = (int)(button_width_dp * scale + 0.5f); // convert dps to pixels
			View current_view = null;

			for(final String supported_option : supported_options) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "supported_option: " + supported_option);
        		int resource = -1;
        		if( icons != null && values != null ) {
            		int index = -1;
            		for(int i=0;i<values.length && index==-1;i++) {
            			if( values[i].equals(supported_option) )
            				index = i;
            		}
            		if( MyDebug.LOG )
            			Log.d(TAG, "index: " + index);
            		if( index != -1 ) {
            			resource = getResources().getIdentifier(icons[index], null, this.getContext().getApplicationContext().getPackageName());
            		}
        		}
    			if( MyDebug.LOG )
    				Log.d(TAG, "addButtonOptionsToPopup time 2.1: " + (System.currentTimeMillis() - debug_time));

        		String button_string;
    			// hacks for ISO mode ISO_HJR (e.g., on Samsung S5)
    			// also some devices report e.g. "ISO100" etc
        		if( prefix_string.length() == 0 ) {
    				button_string = supported_option;
        		}
        		else if( prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 4 && supported_option.substring(0, 4).equalsIgnoreCase("ISO_") ) {
        			button_string = prefix_string + "\n" + supported_option.substring(4);
    			}
    			else if( prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 3 && supported_option.substring(0, 3).equalsIgnoreCase("ISO") ) {
    				button_string = prefix_string + "\n" + supported_option.substring(3);
    			}
    			else {
    				button_string = prefix_string + "\n" + supported_option;
    			}
    			if( MyDebug.LOG )
    				Log.d(TAG, "button_string: " + button_string);
        		View view;
        		if( resource != -1 ) {
        			ImageButton image_button = new ImageButton(this.getContext());
        			if( MyDebug.LOG )
        				Log.d(TAG, "addButtonOptionsToPopup time 2.11: " + (System.currentTimeMillis() - debug_time));
        			view = image_button;
        			ll2.addView(view);
        			if( MyDebug.LOG )
        				Log.d(TAG, "addButtonOptionsToPopup time 2.12: " + (System.currentTimeMillis() - debug_time));

        			//image_button.setImageResource(resource);
        			final MainActivity main_activity = (MainActivity)this.getContext();
        			Bitmap bm = main_activity.getPreloadedBitmap(resource);
        			if( bm != null )
        				image_button.setImageBitmap(bm);
        			else {
            			if( MyDebug.LOG )
            				Log.d(TAG, "failed to find bitmap for resource " + resource + "!");
        			}
        			if( MyDebug.LOG )
        				Log.d(TAG, "addButtonOptionsToPopup time 2.13: " + (System.currentTimeMillis() - debug_time));
        			image_button.setScaleType(ScaleType.FIT_CENTER);
        			final int padding = (int) (10 * scale + 0.5f); // convert dps to pixels
        			view.setPadding(padding, padding, padding, padding);
        		}
        		else {
        			Button button = new Button(this.getContext());
        			button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
        			view = button;
        			ll2.addView(view);

        			button.setText(button_string);
        			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.0f);
        			button.setTextColor(Color.WHITE);
        			// need 0 padding so we have enough room to display text for ISO buttons, when there are 6 ISO settings
        			final int padding = (int) (0 * scale + 0.5f); // convert dps to pixels
        			view.setPadding(padding, padding, padding, padding);
        		}
    			if( MyDebug.LOG )
    				Log.d(TAG, "addButtonOptionsToPopup time 2.2: " + (System.currentTimeMillis() - debug_time));

    			ViewGroup.LayoutParams params = view.getLayoutParams();
    			params.width = button_width;
    			params.height = (int) (50 * scale + 0.5f); // convert dps to pixels
    			view.setLayoutParams(params);

    			view.setContentDescription(button_string);
    			if( supported_option.equals(current_value) ) {
    				view.setAlpha(ALPHA_BUTTON_SELECTED);
    				current_view = view;
    			}
    			else {
    				view.setAlpha(ALPHA_BUTTON);
    			}
    			if( MyDebug.LOG )
    				Log.d(TAG, "addButtonOptionsToPopup time 2.3: " + (System.currentTimeMillis() - debug_time));
    			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if( MyDebug.LOG )
							Log.d(TAG, "clicked: " + supported_option);
						listener.onClick(supported_option);
					}
    			});
    			this.popup_buttons.put(test_key + "_" + supported_option, view);
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "addButtonOptionsToPopup time 2.4: " + (System.currentTimeMillis() - debug_time));
    				Log.d(TAG, "added to popup_buttons: " + test_key + "_" + supported_option + " view: " + view);
    				Log.d(TAG, "popup_buttons is now: " + popup_buttons);
    			}
    		}
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 3: " + (System.currentTimeMillis() - debug_time));
			if( use_scrollview ) {
				if( MyDebug.LOG )
					Log.d(TAG, "using scrollview");
	        	final HorizontalScrollView scroll = new HorizontalScrollView(this.getContext());
	        	scroll.addView(ll2);
	        	{
	    			ViewGroup.LayoutParams params = new LayoutParams(
	    					total_width,
	    			        LayoutParams.WRAP_CONTENT);
	    			scroll.setLayoutParams(params);
	        	}
	        	this.addView(scroll);
	        	if( current_view != null ) {
	        		// scroll to the selected button
	        		final View final_current_view = current_view;
	        		this.getViewTreeObserver().addOnGlobalLayoutListener( 
	        			new OnGlobalLayoutListener() {
							@Override
							public void onGlobalLayout() {
								// scroll so selected button is centred
								int jump_x = final_current_view.getLeft() - (total_width-button_width)/2;
								// scrollTo should automatically clamp to the bounds of the view, but just in case
								jump_x = Math.min(jump_x, total_width-1);
								if( jump_x > 0 ) {
									/*if( MyDebug.LOG )
										Log.d(TAG, "jump to " + jump_X);*/
									scroll.scrollTo(jump_x, 0);
								}
							}
	        			}
	        		);
	        	}
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "not using scrollview");
	    		this.addView(ll2);
			}
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 4: " + (System.currentTimeMillis() - debug_time));
        }
    }
    
    private void addTitleToPopup(final String title) {
		TextView text_view = new TextView(this.getContext());
		text_view.setText(title + ":");
		text_view.setTextColor(Color.WHITE);
		text_view.setGravity(Gravity.CENTER);
		text_view.setTypeface(null, Typeface.BOLD);
    	this.addView(text_view);
    }

    private abstract class ArrayOptionsPopupListener {
		public abstract int onClickPrev();
		public abstract int onClickNext();
    }
    
    private void addArrayOptionsToPopup(final List<String> supported_options, final String title, final boolean title_in_options, final int current_index, final boolean cyclic, final String test_key, final ArrayOptionsPopupListener listener) {
		if( supported_options != null && current_index != -1 ) {
			if( !title_in_options ) {
				addTitleToPopup(title);
			}

			LinearLayout ll2 = new LinearLayout(this.getContext());
            ll2.setOrientation(LinearLayout.HORIZONTAL);
            
			final TextView resolution_text_view = new TextView(this.getContext());
			if( title_in_options )
				resolution_text_view.setText(title + ": " + supported_options.get(current_index));
			else
				resolution_text_view.setText(supported_options.get(current_index));
			resolution_text_view.setTextColor(Color.WHITE);
			resolution_text_view.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
			resolution_text_view.setLayoutParams(params);

			final float scale = getResources().getDisplayMetrics().density;
			final int padding = (int) (0 * scale + 0.5f); // convert dps to pixels
			final int button_w = (int) (60 * scale + 0.5f); // convert dps to pixels
			final int button_h = (int) (30 * scale + 0.5f); // convert dps to pixels
			final Button prev_button = new Button(this.getContext());
			prev_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			ll2.addView(prev_button);
			prev_button.setText("<");
			prev_button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.0f);
			prev_button.setPadding(padding, padding, padding, padding);
			ViewGroup.LayoutParams vg_params = prev_button.getLayoutParams();
			vg_params.width = button_w;
			vg_params.height = button_h;
			prev_button.setLayoutParams(vg_params);
			prev_button.setVisibility( (cyclic || current_index > 0) ? View.VISIBLE : View.INVISIBLE);
			this.popup_buttons.put(test_key + "_PREV", prev_button);

        	ll2.addView(resolution_text_view);
			this.popup_buttons.put(test_key, resolution_text_view);

			final Button next_button = new Button(this.getContext());
			next_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			ll2.addView(next_button);
			next_button.setText(">");
			next_button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.0f);
			next_button.setPadding(padding, padding, padding, padding);
			vg_params = next_button.getLayoutParams();
			vg_params.width = button_w;
			vg_params.height = button_h;
			next_button.setLayoutParams(vg_params);
			next_button.setVisibility( (cyclic || current_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
			this.popup_buttons.put(test_key + "_NEXT", next_button);

			prev_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
        			int new_index = listener.onClickPrev();
        			if( new_index != -1 ) {
        				if( title_in_options )
        					resolution_text_view.setText(title + ": " + supported_options.get(new_index));
        				else
        					resolution_text_view.setText(supported_options.get(new_index));
	        			prev_button.setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
	        			next_button.setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
        			}
				}
			});
			next_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
        			int new_index = listener.onClickNext();
        			if( new_index != -1 ) {
        				if( title_in_options )
        					resolution_text_view.setText(title + ": " + supported_options.get(new_index));
        				else
        					resolution_text_view.setText(supported_options.get(new_index));
	        			prev_button.setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
	        			next_button.setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
        			}
				}
			});

			this.addView(ll2);
    	}
    }


    // for testing
    public View getPopupButton(String key) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "getPopupButton(" + key + "): " + popup_buttons.get(key));
			Log.d(TAG, "this: " + this);
			Log.d(TAG, "popup_buttons: " + popup_buttons);
		}
    	return popup_buttons.get(key);
    }
}
