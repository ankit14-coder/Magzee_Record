package com.magzee.magzee;

import java.util.HashMap;
import java.util.Map;

public class Const {
    public enum ASPECT_RATIO {
        AR16_9(1.7777778f), AR18_9(2f);

        private static Map<Float, ASPECT_RATIO> map = new HashMap<Float, ASPECT_RATIO>();

        static {
            for (ASPECT_RATIO aspectRatio : ASPECT_RATIO.values()) {
                map.put(aspectRatio.numVal, aspectRatio);
            }
        }

        private float numVal;

        ASPECT_RATIO(float numVal) {
            this.numVal = numVal;
        }

        public static ASPECT_RATIO valueOf(float val) {
            return map.get(val) == null ? AR16_9 : map.get(val);
        }
    }

    public static final int VIDEO_EDIT_REQUEST_CODE = 1004;
    public static final int VIDEO_EDIT_RESULT_CODE = 1005;
    public static final String TAG = "SCREENRECORDER_LOG";
    public static final String APPDIR = "screenrecorder";
    public static final String ALERT_EXTR_STORAGE_CB_KEY = "ext_dir_warn_donot_show_again";
    public static final String VIDEO_EDIT_URI_KEY = "edit_video";
    public static final int EXTDIR_REQUEST_CODE = 1000;
    public static final int AUDIO_REQUEST_CODE = 1001;
    public static final int FLOATING_CONTROLS_SYSTEM_WINDOWS_CODE = 1002;
    public static final int SCREEN_RECORD_REQUEST_CODE = 1003;
    public static final int CAMERA_REQUEST_CODE = 1006;
    public static final int CAMERA_SYSTEM_WINDOWS_CODE = 1007;
    public static final int INTERNAL_AUDIO_REQUEST_CODE = 1008;
    public static final int INTERNAL_R_SUBMIX_AUDIO_REQUEST_CODE = 1009;
    public static final String SCREEN_RECORDING_START = "com.example.screenrecord7.services.action.startrecording";
    public static final String SCREEN_RECORDING_PAUSE = "com.example.screenrecord7.services.action.pauserecording";
    public static final String SCREEN_RECORDING_RESUME = "com.example.screenrecord7.services.action.resumerecording";
    public static final String SCREEN_RECORDING_STOP = "com.example.screenrecord7.services.action.stoprecording";
    public static final String SCREEN_RECORDING_DESTORY_SHAKE_GESTURE = "com.orpheusdroid.screenrecorder.services.action.destoryshakegesture";
    public static final String SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT = "com.orpheusdroid.screenrecorder.SHOWVIDEOSLIST";
    public static final int SCREEN_RECORDER_NOTIFICATION_ID = 5001;
    public static final int SCREEN_RECORDER_SHARE_NOTIFICATION_ID = 5002;
    public static final int SCREEN_RECORDER_WAITING_FOR_SHAKE_NOTIFICATION_ID = 5003;
    public static final String RECORDER_INTENT_DATA = "recorder_intent_data";
    public static final String RECORDER_INTENT_RESULT = "recorder_intent_result";
    public static final String RECORDING_NOTIFICATION_CHANNEL_ID = "recording_notification_channel_id1";
    public static final String SHARE_NOTIFICATION_CHANNEL_ID = "share_notification_channel_id1";
    public static final String RECORDING_NOTIFICATION_CHANNEL_NAME = "Shown Persistent notification when recording screen or when waiting for shake gesture";
    public static final String SHARE_NOTIFICATION_CHANNEL_NAME = "Show Notification to share or edit the recorded video";
    public static final String ANALYTICS_URL = "https://analytics.orpheusdroid.com";
    public static final String ANALYTICS_API_KEY = "07273a5c91f8a932685be1e3ad0d160d3de6d4ba";
    public static final String COUNTLY_USAGE_STATS_GROUP_NAME = "analytics_group";
    public static final String CHANGELOG_VER = "Changelog_ver";

    public static final String PREFS_REQUEST_ANALYTICS_PERMISSION = "request_analytics_permission";
    public static final String PREFS_WHITE_THEME = "white_theme";
    public static final String PREFS_LIGHT_THEME = "light_theme";
    public static final String PREFS_DARK_THEME = "dark_theme";
    public static final String PREFS_BLACK_THEME = "black_theme";
    public static final String PREFS_CAMERA_OVERLAY_POS = "camera_overlay_pos";
    public static final String PREFS_INTERNAL_AUDIO_DIALOG_KEY = "int_audio_diag";

    public enum RecordingState {
        RECORDING, PAUSED, STOPPED
    }

    public enum analytics {
        CRASHREPORTING, USAGESTATS
    }
}
