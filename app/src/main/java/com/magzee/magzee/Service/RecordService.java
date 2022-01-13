package com.magzee.magzee.Service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.magzee.magzee.Const;
import com.magzee.magzee.MainActivity;
import com.magzee.magzee.R;
import com.magzee.magzee.ScreenRecordActivity;
import com.magzee.magzee.ui.EditVideoActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class RecordService extends Service {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private int WIDTH, HEIGHT, FPS, DENSITY_DPI;
    private int BITRATE;
    private String audioRecSource;
    private String SAVEPATH;
    private AudioManager mAudioManager;
    private Surface msurface;
    private SurfaceView msurfaceView;

    public Surface getMsurface() {
        return ScreenRecordActivity.surface;
    }
    public SurfaceView getMsurfaceView() {
        return ScreenRecordActivity.mSurfaceView;
    }

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private int screenOrientation;
    private String saveLocation;

    private boolean isRecording;


    private boolean isBound = false;
    private boolean showSysUIDemo = false;
    private NotificationManager mNotificationManager;
    private Intent data;
    private int result;

    private long startTime, elapsedTime = 0;
    private SharedPreferences prefs;
    private WindowManager window;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            Toast.makeText(RecordService.this, R.string.screen_recording_stopped_toast, Toast.LENGTH_SHORT).show();
            showShareNotification();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannels();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (!isRecording){
            screenOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            data= intent.getParcelableExtra(Const.RECORDER_INTENT_DATA);
            result = intent.getIntExtra(Const.RECORDER_INTENT_RESULT, Activity.RESULT_OK);
            getValues();
            startRecording();
        }
        else {
                Toast.makeText(this, R.string.screenrecording_already_active_toast, Toast.LENGTH_SHORT).show();
        }
        return START_STICKY;

    }

    private void startRecording() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //startTime is to calculate elapsed recording time to update notification during pause/resume
            startTime = System.currentTimeMillis();
            Intent recordPauseIntent = new Intent(this, RecordService.class);
            recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
            PendingIntent precordPauseIntent = PendingIntent.getService(this, 0, recordPauseIntent, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_pause_white,
                    getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);

            //Start Notification as foreground
            startNotificationForeGround(createRecordingNotification(action).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        } else
            startNotificationForeGround(createRecordingNotification(null).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);

        //Initialize MediaRecorder class and initialize it with preferred configuration
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnErrorListener((mr, what, extra) -> {
            Log.e(Const.TAG, "Screencam Error: " + what + ", Extra: " + extra);
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            destroyMediaProjection();
        });
        mMediaRecorder.setOnInfoListener((mr, what, extra) -> {
            Log.d(Const.TAG, "Screencam Info: " + what + ", Extra: " + extra);
        });
        Toast.makeText(this, "in startRecording", Toast.LENGTH_SHORT).show();
        initRecorder();
        //Set Callback for MediaProjection
        mMediaProjectionCallback = new MediaProjectionCallback();
        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //Initialize MediaProjection using data received from Intent
        mMediaProjection = mProjectionManager.getMediaProjection(result, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);

        /* Create a new virtual display with the actual default display
         * and pass it on to MediaRecorder to start recording */
        mVirtualDisplay = createVirtualDisplay();
        try {
            //mMediaRecorder.start();
        }catch (IllegalStateException e){
            Log.e(Const.TAG, "Mediarecorder reached Illegal state exception. Did you start the recording twice?");
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            isRecording = false;
            mMediaProjection.stop();
            stopSelf();
        }


    }



    @SuppressLint("WrongConstant")
    private void initRecorder() {
        Toast.makeText(this, "in initRecorder start", Toast.LENGTH_SHORT).show();
        boolean mustRecAudio = false;
        try {
            String audioBitRate = prefs.getString(getString(R.string.audiobitrate_key), "192");
            String audioSamplingRate = prefs.getString(getString(R.string.audiosamplingrate_key), getBestSampleRate() + "");
            String audioChannel = prefs.getString(getString(R.string.audiochannels_key), "1");
            switch (audioRecSource) {
                case "1":
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mustRecAudio = true;
                    break;
                case "2":
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                    mMediaRecorder.setAudioEncodingBitRate(Integer.parseInt(audioBitRate));
                    mMediaRecorder.setAudioSamplingRate(Integer.parseInt(audioSamplingRate));
                    mMediaRecorder.setAudioChannels(Integer.parseInt(audioChannel));
                    mustRecAudio = true;

                    Log.d(Const.TAG, "bit rate: " + audioBitRate + " sampling: " + audioSamplingRate + " channel" + audioChannel);
                    break;
                case "3":
                    mAudioManager.setParameters("screenRecordAudioSource=8");
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
                    mMediaRecorder.setAudioEncodingBitRate(Integer.parseInt(audioBitRate));
                    mMediaRecorder.setAudioSamplingRate(Integer.parseInt(audioSamplingRate));
                    mMediaRecorder.setAudioChannels(Integer.parseInt(audioChannel));
                    mustRecAudio = true;
                    break;
            }

            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (mustRecAudio)
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncoder(getBestVideoEncoder());
            mMediaRecorder.setVideoSize(WIDTH, HEIGHT);
            mMediaRecorder.setVideoFrameRate(FPS);
            mMediaRecorder.setOutputFile(SAVEPATH);
            mMediaRecorder.setVideoEncodingBitRate(BITRATE);
            mMediaRecorder.setMaxFileSize(getFreeSpaceInBytes());


            mMediaRecorder.prepare();

            msurface= mMediaRecorder.getSurface();
        } catch (IOException e) {
            e.printStackTrace();
        }
        catch (IllegalStateException illegalStateException){
            Toast.makeText(this, "in illegalstate", Toast.LENGTH_SHORT).show();
            illegalStateException.printStackTrace();
        }
    }
    private long getFreeSpaceInBytes() {
        StatFs FSStats = new StatFs(saveLocation);
        long bytesAvailable = FSStats.getAvailableBytes();// * FSStats.getBlockCountLong();
        Log.d(Const.TAG, "Free space in GB: " + bytesAvailable / (1000 * 1000 * 1000));
        return bytesAvailable;
    }
    private int getBestVideoEncoder() {
        int VideoCodec = MediaRecorder.VideoEncoder.DEFAULT;
        if (getMediaCodecFor(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                VideoCodec = MediaRecorder.VideoEncoder.HEVC;
            }
        } else if (getMediaCodecFor(MediaFormat.MIMETYPE_VIDEO_AVC))
            VideoCodec = MediaRecorder.VideoEncoder.H264;
        return VideoCodec;
    }

    //Virtual display created by mirroring the actual physical display
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                getMsurfaceView().getWidth(),
                getMsurfaceView().getHeight(),
                DENSITY_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                getMsurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    @TargetApi(24)
    private void resumeScreenRecording() {
        mMediaRecorder.resume();
        //Reset startTime to current time again
        startTime = System.currentTimeMillis();
        //set Pause action to Notification and update current Notification
        Intent recordPauseIntent = new Intent(this, RecordService.class);
        recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
        PendingIntent precordPauseIntent = PendingIntent.getService(this, 0, recordPauseIntent, 0);
        NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_pause_white,
                getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);
        updateNotification(createRecordingNotification(action).setUsesChronometer(true)
                .setWhen((System.currentTimeMillis() - elapsedTime)).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        Toast.makeText(this, R.string.screen_recording_resumed_toast, Toast.LENGTH_SHORT).show();
    }


    @TargetApi(24)
    private void pauseScreenRecording() {
        mMediaRecorder.pause();
        elapsedTime += (System.currentTimeMillis() - startTime);

        //Set Resume action to Notification and update the current notification
        Intent recordResumeIntent = new Intent(this, RecordService.class);
        recordResumeIntent.setAction(Const.SCREEN_RECORDING_RESUME);
        PendingIntent precordResumeIntent = PendingIntent.getService(this, 0, recordResumeIntent, 0);
        NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_play_arrow_white,
                getString(R.string.screen_recording_notification_action_resume), precordResumeIntent);
        updateNotification(createRecordingNotification(action).setUsesChronometer(false).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        Toast.makeText(this, R.string.screen_recording_paused_toast, Toast.LENGTH_SHORT).show();


    }
    //Start service as a foreground service. We dont want the service to be killed in case of low memory
    private void startNotificationForeGround(Notification notification, int ID) {
        startForeground(ID, notification);
    }
    private void showShareNotification() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_notification_big);

        Uri videoUri = FileProvider.getUriForFile(
                this, "com.example.screenrecord7" + ".provider",
                new File(SAVEPATH));

        Intent Shareintent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, videoUri)
                .setType("video/mp4");

        Intent editIntent = new Intent(this, EditVideoActivity.class);
        editIntent.putExtra(Const.VIDEO_EDIT_URI_KEY, SAVEPATH);
        PendingIntent editPendingIntent = PendingIntent.getActivity(this, 0, editIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(this, 0, Intent.createChooser(
                Shareintent, getString(R.string.share_intent_title)), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setAction(Const.SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder shareNotification = new NotificationCompat.Builder(this, Const.SHARE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.share_intent_notification_title))
                .setContentText(getString(R.string.share_intent_notification_content))
                .setSmallIcon(R.mipmap.ic_notification)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_share, getString(R.string.share_intent_notification_action_text)
                        , sharePendingIntent)
                .addAction(android.R.drawable.ic_menu_edit, getString(R.string.edit_intent_notification_action_text)
                        , editPendingIntent);
        updateNotification(shareNotification.build(), Const.SCREEN_RECORDER_SHARE_NOTIFICATION_ID);
    }

    //Add notification channel for supporting Notification in Api 26 (Oreo)
    @TargetApi(26)
    private void createNotificationChannels() {
        List<NotificationChannel> notificationChannels = new ArrayList<>();
        NotificationChannel recordingNotificationChannel = new NotificationChannel(
                Const.RECORDING_NOTIFICATION_CHANNEL_ID,
                Const.RECORDING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        recordingNotificationChannel.enableLights(true);
        recordingNotificationChannel.setLightColor(Color.RED);
        recordingNotificationChannel.setShowBadge(true);
        recordingNotificationChannel.enableVibration(true);
        recordingNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(recordingNotificationChannel);

        NotificationChannel shareNotificationChannel = new NotificationChannel(
                Const.SHARE_NOTIFICATION_CHANNEL_ID,
                Const.SHARE_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        shareNotificationChannel.enableLights(true);
        shareNotificationChannel.setLightColor(Color.RED);
        shareNotificationChannel.setShowBadge(true);
        shareNotificationChannel.enableVibration(true);
        shareNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(shareNotificationChannel);

        getManager().createNotificationChannels(notificationChannels);
    }

    /* Create Notification.Builder with action passed in case user's android version is greater than
     * API24 */
    private NotificationCompat.Builder createRecordingNotification(NotificationCompat.Action action) {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_notification_big);

        Intent recordStopIntent = new Intent(this, RecordService.class);
        recordStopIntent.setAction(Const.SCREEN_RECORDING_STOP);
        PendingIntent precordStopIntent = PendingIntent.getService(this, 0, recordStopIntent, 0);

        Intent UIIntent = new Intent(this, MainActivity.class);
        PendingIntent notificationContentIntent = PendingIntent.getActivity(this, 0, UIIntent, 0);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, Const.RECORDING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.screen_recording_notification_title))
                .setTicker(getResources().getString(R.string.screen_recording_notification_title))
                .setSmallIcon(R.mipmap.ic_notification)
                //.setLargeIcon(
                //Bitmap.createScaledBitmap(icon, 64, 64, false))
                .setLargeIcon(icon)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setContentIntent(notificationContentIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .setPriority(Notification.PRIORITY_MAX);
        if (action != null)
            notification.addAction(action);
        return notification;
    }

    //Update existing notification with its ID and new Notification data
    private void updateNotification(Notification notification, int ID) {
        getManager().notify(ID, notification);
    }
    private NotificationManager getManager() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }

    private void stopRecording() {
        stopScreenSharing();

        //The service is started as foreground service and hence has to be stopped
        stopForeground(true);
    }
    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            Log.d(Const.TAG, "Virtual display is null. Screen sharing already stopped");
            return;
        }
        destroyMediaProjection();
    }
    public int getBestSampleRate() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int samplingRate = (sampleRateString == null) ? 44100 : Integer.parseInt(sampleRateString);
        Log.d(Const.TAG, "Sampling rate: " + samplingRate);
        return samplingRate;
    }
    private boolean getMediaCodecFor(String format) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                format,
                WIDTH,
                HEIGHT
        );
        String encoder = list.findEncoderForFormat(mediaFormat);
        if (encoder == null) {
            Log.d("Null Encoder: ", format);
            return false;
        }
        Log.d("Encoder", encoder);
        return !encoder.startsWith("OMX.google");
    }

    //Get user's choices for user choosable settings
    public void getValues() {
        String res = getResolution();
        setWidthHeight(res);
        FPS = Integer.parseInt(prefs.getString(getString(R.string.fps_key), "30"));
        BITRATE = Integer.parseInt(prefs.getString(getString(R.string.bitrate_key), "7130317"));
        audioRecSource = prefs.getString(getString(R.string.audiorec_key), "0");
        saveLocation = prefs.getString(getString(R.string.savelocation_key),
                Environment.getExternalStorageDirectory() + File.separator + Const.APPDIR);
        File saveDir = new File(saveLocation);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !saveDir.isDirectory()) {
            saveDir.mkdirs();
        }

        showSysUIDemo = prefs.getBoolean(getString(R.string.preference_sysui_demo_mode_key), false);

        String saveFileName = getFileSaveName();
        SAVEPATH = saveLocation + File.separator + saveFileName + ".mp4";
        Log.d("getvalues", String.valueOf(FPS));
        Log.d("getvalues", String.valueOf(BITRATE));
        Log.d("getvalues", audioRecSource);
        Log.d("getvalues", saveLocation);
        Log.d("getvalues", saveFileName);

    }
    /* The PreferenceScreen save values as string and we save the user selected video resolution as
     * WIDTH x HEIGHT. Lets split the string on 'x' and retrieve width and height */
    private void setWidthHeight(String res) {
        String[] widthHeight = res.split("x");
        String orientationPrefs = prefs.getString(getString(R.string.orientation_key), "auto");
        switch (orientationPrefs) {
            case "auto":
                if (screenOrientation == 0 || screenOrientation == 2) {
                    WIDTH = Integer.parseInt(widthHeight[0]);
                    HEIGHT = Integer.parseInt(widthHeight[1]);
                } else {
                    HEIGHT = Integer.parseInt(widthHeight[0]);
                    WIDTH = Integer.parseInt(widthHeight[1]);
                }
                break;
            case "portrait":
                WIDTH = Integer.parseInt(widthHeight[0]);
                HEIGHT = Integer.parseInt(widthHeight[1]);
                break;
            case "landscape":
                HEIGHT = Integer.parseInt(widthHeight[0]);
                WIDTH = Integer.parseInt(widthHeight[1]);
                break;
        }
        Log.d(Const.TAG, "Width: " + WIDTH + ",Height:" + HEIGHT);
    }
    //Get the device resolution in pixels
    private String getResolution() {
        DisplayMetrics metrics = new DisplayMetrics();
        window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        window.getDefaultDisplay().getRealMetrics(metrics);
        DENSITY_DPI = metrics.densityDpi;
        int width = metrics.widthPixels;
        width = Integer.parseInt(prefs.getString(getString(R.string.res_key), Integer.toString(width)));
        float aspectRatio = getAspectRatio(metrics);
        int height = calculateClosestHeight(width, aspectRatio);
        //String res = width + "x" + (int) (width * getAspectRatio(metrics));
        String res = width + "x" + height;
        Log.d(Const.TAG, "resolution service: " + "[Width: "
                + width + ", Height: " + width * aspectRatio + ", aspect ratio: " + aspectRatio + "]");
        return res;
    }
    private int calculateClosestHeight(int width, float aspectRatio) {
        int calculatedHeight = (int) (width * aspectRatio);
        Log.d(Const.TAG, "Calculated width=" + calculatedHeight);
        Log.d(Const.TAG, "Aspect ratio: " + aspectRatio);
        if (calculatedHeight / 16 != 0) {
            int quotient = calculatedHeight / 16;
            Log.d(Const.TAG, calculatedHeight + " not divisible by 16");

            calculatedHeight = 16 * quotient;

            Log.d(Const.TAG, "Maximum possible height is " + calculatedHeight);
        }
        return calculatedHeight;
    }
    private float getAspectRatio(DisplayMetrics metrics) {
        float screen_width = metrics.widthPixels;
        float screen_height = metrics.heightPixels;
        float aspectRatio;
        if (screen_width > screen_height) {
            aspectRatio = screen_width / screen_height;
        } else {
            aspectRatio = screen_height / screen_width;
        }
        return aspectRatio;
    }

    //Return filename of the video to be saved formatted as chosen by the user
    private String getFileSaveName() {
        String filename = prefs.getString(getString(R.string.filename_key), "yyyyMMdd_HHmmss");

        //Required to handle preference change
        filename = filename.replace("hh", "HH");
        String prefix = prefs.getString(getString(R.string.fileprefix_key), "recording");
        Date today = Calendar.getInstance().getTime();
        SimpleDateFormat formatter = new SimpleDateFormat(filename);
        return prefix + "_" + formatter.format(today);
    }
    private void destroyMediaProjection() {
        this.mAudioManager.setParameters("screenRecordAudioSource=0");
        try {
            mMediaRecorder.stop();
            indexFile();
            Log.i(Const.TAG, "MediaProjection Stopped");
        } catch (RuntimeException e) {
            Log.e(Const.TAG, "Fatal exception! Destroying media projection failed." + "\n" + e.getMessage());
            if (new File(SAVEPATH).delete())
                Log.d(Const.TAG, "Corrupted file delete successful");
            Toast.makeText(this, getString(R.string.fatal_exception_message), Toast.LENGTH_SHORT).show();
        } finally {
            mMediaRecorder.reset();
            mVirtualDisplay.release();
            mMediaRecorder.release();
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            stopSelf();
        }
        isRecording = false;
    }
    /* Its weird that android does not index the files immediately once its created and that causes
     * trouble for user in finding the video in gallery. Let's explicitly announce the file creation
     * to android and index it */
    private void indexFile() {
        //Create a new ArrayList and add the newly created video file path to it
        ArrayList<String> toBeScanned = new ArrayList<>();
        toBeScanned.add(SAVEPATH);
        String[] toBeScannedStr = new String[toBeScanned.size()];
        toBeScannedStr = toBeScanned.toArray(toBeScannedStr);

        //Request MediaScannerConnection to scan the new file and index it
        MediaScannerConnection.scanFile(this, toBeScannedStr, null, new MediaScannerConnection.OnScanCompletedListener() {

            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.i(Const.TAG, "SCAN COMPLETED: " + path);
                //Show toast on main thread
                Message message = mHandler.obtainMessage();
                message.sendToTarget();
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.d(Const.TAG, "Recorder service destroyed");
        super.onDestroy();
    }
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.v(Const.TAG, "Recording Stopped");
            stopScreenSharing();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
