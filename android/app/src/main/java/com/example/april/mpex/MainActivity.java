/*
Source written by hyeongseok kim, 2018.
 */
package com.example.april.mpex;

import android.app.Activity;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "lmpex";
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_MEDIA_CAPTURE = 100;
    public MediaProjection sMediaProjection;
    private MediaProjectionManager mProjectionManager;

    private BluetoothService btService = null;

    private Button startButton;
    private Button stopButton;
    private Button connectButton;



    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }

    };


    /****************************************** Activity Lifecycle methods ************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"MainAcvitity onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // call for the projection manager
        CaptureService.mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mProjectionManager = CaptureService.mProjectionManager;
        CaptureService.createBluetoothService(this);
        btService = CaptureService.getBluetoothService();


        // start projection
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startProjection();
            }
        });

        // stop projection
        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                stopProjection();
            }
        });

        // connect bluetooth
        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(btService.getDeviceState()){
                    btService.enableBluetooth();
                } else {
                    finish();
                }
            }
        });





    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    btService.getDeviceInfo(data);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Next Step
                    btService.scanDevice();
                } else {
                    Log.d(TAG, "Bluetooth is not enabled");
                }
                break;
            case REQUEST_MEDIA_CAPTURE :
                CaptureService.sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
                sMediaProjection = CaptureService.sMediaProjection;
                if (sMediaProjection != null) {
                    Intent intent = new Intent(getApplicationContext(),CaptureService.class);
                    startService(intent);
                }
                break;
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d("lmpex","MainActivityDestroyed");
    }

    /****************************************** UI Widget Callbacks *******************************/
    private void startProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_CAPTURE);
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sMediaProjection != null) {
                    sMediaProjection.stop();
                }
            }
        });
    }

    /****************************************** Factoring Virtual Display creation ****************/

}
