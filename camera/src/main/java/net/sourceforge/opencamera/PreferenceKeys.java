package net.sourceforge.opencamera;

/** Stores all of the string keys used for SharedPreferences.
 */
public class PreferenceKeys {
    // must be static, to safely call from other Activities
	
	// arguably the static methods here that don't receive an argument could just be static final strings? Though we may want to change some of them to be cameraId-specific in future

	/** If this preference is set, no longer show the intro dialog.
	 */
    public static String getFirstTimePreferenceKey() {
        return "done_first_time";
    }

    public static String getUseCamera2PreferenceKey() {
    	return "preference_use_camera2";
    }

    public static String getFlashPreferenceKey(int cameraId) {
    	return "flash_value_" + cameraId;
    }

    public static String getResolutionPreferenceKey(int cameraId) {
    	return "camera_resolution_" + cameraId;
    }
    
    public static String getVideoQualityPreferenceKey(int cameraId) {
    	return "video_quality_" + cameraId;
    }
    
    public static String getIsVideoPreferenceKey() {
    	return "is_video";
    }

    public static String getSceneModePreferenceKey() {
    	return "preference_scene_mode";
    }

    public static String getISOPreferenceKey() {
    	return "preference_iso";
    }

    public static String getRawPreferenceKey() {
    	return "preference_raw";
    }

    public static String getAudioControlPreferenceKey() {
    	return "preference_audio_control";
    }
    
    public static String getAudioNoiseControlSensitivityPreferenceKey() {
    	return "preference_audio_noise_control_sensitivity";
    }
    
    public static String getQualityPreferenceKey() {
    	return "preference_quality";
    }
    
    public static String getAutoStabilisePreferenceKey() {
    	return "preference_auto_stabilise";
    }

    public static String getFrontCameraMirrorKey() {
        return "preference_front_camera_mirror";
    }

    public static String getBackgroundPhotoSavingPreferenceKey() {
    	return "preference_background_photo_saving";
    }
    
    public static String getCamera2FakeFlashPreferenceKey() {
    	return "preference_camera2_fake_flash";
    }

    public static String getCamera2FastBurstPreferenceKey() {
        return "preference_camera2_fast_burst";
    }

    public static String getUIPlacementPreferenceKey() {
    	return "preference_ui_placement";
    }

    public static String getPausePreviewPreferenceKey() {
    	return "preference_pause_preview";
    }

    public static String getShowToastsPreferenceKey() {
    	return "preference_show_toasts";
    }

    public static String getTakePhotoBorderPreferenceKey() {
    	return "preference_take_photo_border";
    }

    public static String getKeepDisplayOnPreferenceKey() {
    	return "preference_keep_display_on";
    }

    public static String getMaxBrightnessPreferenceKey() {
    	return "preference_max_brightness";
    }

    public static String getShowISOPreferenceKey() {
    	return "preference_show_iso";
    }

    public static String getCalibratedLevelAnglePreferenceKey() {
        return "preference_calibrate_level_angle";
    }
    
    public static String getShowFreeMemoryPreferenceKey() {
    	return "preference_free_memory";
    }
    
    public static String getShowTimePreferenceKey() {
    	return "preference_show_time";
    }

    public static String getShowGridPreferenceKey() {
    	return "preference_grid";
    }
    
    public static String getShowCropGuidePreferenceKey() {
    	return "preference_crop_guide";
    }
    
    public static String getFaceDetectionPreferenceKey() {
    	return "preference_face_detection";
    }

    public static String getVideoStabilizationPreferenceKey() {
    	return "preference_video_stabilization";
    }

    public static String getVideoBitratePreferenceKey() {
    	return "preference_video_bitrate";
    }

    public static String getVideoFPSPreferenceKey() {
    	return "preference_video_fps";
    }
    
    public static String getVideoMaxDurationPreferenceKey() {
    	return "preference_video_max_duration";
    }
    
    public static String getVideoRestartPreferenceKey() {
    	return "preference_video_restart";
    }
    
    public static String getVideoMaxFileSizePreferenceKey() {
    	return "preference_video_max_filesize";
    }
    
    public static String getVideoRestartMaxFileSizePreferenceKey() {
    	return "preference_video_restart_max_filesize";
    }

    public static String getVideoFlashPreferenceKey() {
    	return "preference_video_flash";
    }

    public static String getLockVideoPreferenceKey() {
    	return "preference_lock_video";
    }
    
    public static String getRecordAudioPreferenceKey() {
    	return "preference_record_audio";
    }

    public static String getRecordAudioChannelsPreferenceKey() {
    	return "preference_record_audio_channels";
    }

    public static String getRecordAudioSourcePreferenceKey() {
    	return "preference_record_audio_src";
    }

    public static String getRotatePreviewPreferenceKey() {
    	return "preference_rotate_preview";
    }

    public static String getLockOrientationPreferenceKey() {
    	return "preference_lock_orientation";
    }

    public static String getTimerPreferenceKey() {
    	return "preference_timer";
    }
    
    public static String getTimerBeepPreferenceKey() {
    	return "preference_timer_beep";
    }
    
    public static String getTimerSpeakPreferenceKey() {
    	return "preference_timer_speak";
    }
    
    public static String getBurstModePreferenceKey() {
    	return "preference_burst_mode";
    }
    
    public static String getBurstIntervalPreferenceKey() {
    	return "preference_burst_interval";
    }
    
    public static String getShutterSoundPreferenceKey() {
    	return "preference_shutter_sound";
    }
    
    public static String getImmersiveModePreferenceKey() {
    	return "preference_immersive_mode";
    }
}
