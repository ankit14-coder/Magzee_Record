package com.magzee.magzee;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.magzee.magzee.Service.RecordService;

public class ScreenRecordActivity extends AppCompatActivity {

    public static Surface surface;
    public static SurfaceView mSurfaceView;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private FloatingActionButton fab;
    private SharedPreferences prefs;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_record);
        getSupportActionBar().hide();

        mSurfaceView =(SurfaceView) findViewById(R.id.surfaceView);
        surface = mSurfaceView.getHolder().getSurface();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        fab = findViewById(R.id.fab);
        //Acquiring media projection service to start screen mirroring
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (isServiceRunning(RecordService.class)) {
            Log.d(Const.TAG, "service is running");
        }
        fab.setOnClickListener(view -> {
            if (mMediaProjection == null && !isServiceRunning(RecordService.class)) {
                //Request Screen recording permission
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Const.SCREEN_RECORD_REQUEST_CODE);
            } else if (isServiceRunning(RecordService.class)) {
                //stop recording if the service is already active and recording
                Toast.makeText(ScreenRecordActivity.this, "Screen already recording", Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //The user has denied permission for screen mirroring. Let's notify the user
        if (resultCode == RESULT_CANCELED && requestCode == Const.SCREEN_RECORD_REQUEST_CODE) {
            Toast.makeText(this,getString(R.string.screen_recording_permission_denied), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent recorderService = new Intent(this, RecordService.class);
        recorderService.setAction(Const.SCREEN_RECORDING_START);
        recorderService.putExtra(Const.RECORDER_INTENT_DATA, data);
        recorderService.putExtra(Const.RECORDER_INTENT_RESULT, resultCode);


        startService(recorderService);

    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}