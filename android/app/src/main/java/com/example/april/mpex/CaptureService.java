package com.example.april.mpex;

import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class CaptureService extends Service{

    private static final String SCREENCAP_NAME = "screencap";
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    public static MediaProjectionManager mProjectionManager;
    public static MediaProjection sMediaProjection;
    private Display mDisplay;
    private OrientationChangeCallback mOrientationChangeCallback;
    private ImageReader mImageReader;
    private int mRotation;
    private Handler mHandler;
    private static final String TAG = Constraints.TAG;
    private static BluetoothService bluetoothService;

    int[] colorpoints = new int[30];
    int[][] colorspaces = new int[30][32400];
    @Override
    public IBinder onBind(Intent intent){
        return null;
    }
    @Override
    public void onCreate(){
        super.onCreate();
        Log.d("lmpex.captureservice","onCreate");
        mHandler = new Handler();

    }
    @Override
    public int onStartCommand(Intent intent, int flags,int startId){
        Notification notiEx = new NotificationCompat.Builder(CaptureService.this,"M_CH_ID")
                .setContentTitle("Title Example")
                .setContentText("Content Text Example")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notiEx);


        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();

        try{
            Thread.sleep(5000);
        }catch(Exception e){
            //
        }

        Log.d("lmpex.captureservice","onStartCommand");

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mDensity = metrics.densityDpi;
        WindowManager window = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        mDisplay = window.getDefaultDisplay();

        // create virtual display depending on device width / height
        createVirtualDisplay();

        // register orientation change callback
        mOrientationChangeCallback = new OrientationChangeCallback(this);
        if (mOrientationChangeCallback.canDetectOrientation()) {
            mOrientationChangeCallback.enable();
        }

        // register media projection stop callback
        sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
        return super.onStartCommand(intent,flags,startId);

    }
    @Override
    public void onDestroy(){
        Log.d("lmpex.captureservice","onDestroy");
        super.onDestroy();
    }

    private void createVirtualDisplay() {
        // get width and height
        Point size = new Point();
        mDisplay.getSize(size);
        mWidth = size.x;
        mHeight = size.y;

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        Log.d("lmpex","createvirtualdisplay");
        if(sMediaProjection == null){
            Log.d("lmpex","sMediaisnull");
        }
        mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        if(mVirtualDisplay == null){
            Log.d("lmpex","svirtualisnull");
        }
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    public static BluetoothService getBluetoothService() {
        return bluetoothService;
    }
    public static void createBluetoothService(Activity ac){
        bluetoothService = new BluetoothService(ac);
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e("ScreenCapture", "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mOrientationChangeCallback != null) mOrientationChangeCallback.disable();
                    sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }
    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private int getLedval(int[] pixels,int width,int height){
        int rsum = 0;
        int gsum = 0;
        int bsum = 0;
        int len = width*height;
        //Log.d(TAG,"getledval"+len);
        for(int i = 0;i < len;i ++){
            rsum += (pixels[i]>>16)&0xff;
            gsum += (pixels[i]>>8)&0xff;
            bsum += pixels[i]&0xff;
        }
        int ravg = rsum / len;
        int gavg = gsum / len;
        int bavg = bsum / len;
        int ret = 0;
        ret = (ravg<<16) + (gavg << 8) + (bavg);
        return ret;
    }
    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // Log.d("lmpex","onImageAvailable called");
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    // create bitmap
                    bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    int wstr = (1861-30-58)/9;
                    int hstr = (1127-30)/6;
                    bitmap.getPixels(colorspaces[0],0,30,58 ,0, 30,30);
                    bitmap.getPixels(colorspaces[1],0,30,58 + wstr,0,30,30);
                    bitmap.getPixels(colorspaces[2],0,30,58 + wstr*2,0,30,30);
                    bitmap.getPixels(colorspaces[3],0,30,58 + wstr*3,0,30,30);
                    bitmap.getPixels(colorspaces[4],0,30,58 + wstr*4,0,30,30);
                    bitmap.getPixels(colorspaces[5],0,30,58 + wstr*5,0,30,30);
                    bitmap.getPixels(colorspaces[6],0,30,58 + wstr*6,0,30,30);
                    bitmap.getPixels(colorspaces[7],0,30,58 + wstr*7,0,30,30);
                    bitmap.getPixels(colorspaces[8],0,30,58 + wstr*8,0,30,30);
                    bitmap.getPixels(colorspaces[9],0,30,58 + wstr*9,0,30,30);

                    bitmap.getPixels(colorspaces[10],0,30,58 + wstr*9,hstr,30,30);
                    bitmap.getPixels(colorspaces[11],0,30,58 + wstr*9,hstr*2,30,30);
                    bitmap.getPixels(colorspaces[12],0,30,58 + wstr*9,hstr*3,30,30);
                    bitmap.getPixels(colorspaces[13],0,30,58 + wstr*9,hstr*4,30,30);
                    bitmap.getPixels(colorspaces[14],0,30,58 + wstr*9,hstr*5,30,30);
                    bitmap.getPixels(colorspaces[15],0,30,58 + wstr*9,hstr*6,30,30);

                    bitmap.getPixels(colorspaces[16],0,30,58 + wstr*8,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[17],0,30,58 + wstr*7,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[18],0,30,58 + wstr*6,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[19],0,30,58 + wstr*5,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[20],0,30,58 + wstr*4,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[21],0,30,58 + wstr*3,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[22],0,30,58 + wstr*2,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[23],0,30,58 + wstr*1,hstr*6,30,30);
                    bitmap.getPixels(colorspaces[24],0,30,58, hstr*6,30, 30);

                    bitmap.getPixels(colorspaces[25],0,30,58,hstr*5,30,30);
                    bitmap.getPixels(colorspaces[26],0,30,58,hstr*4,30,30);
                    bitmap.getPixels(colorspaces[27],0,30,58,hstr*3,30,30);
                    bitmap.getPixels(colorspaces[28],0,30,58,hstr*2,30,30);
                    bitmap.getPixels(colorspaces[29],0,30,58,hstr*1,30,30);

                    colorpoints[19] = getLedval(colorspaces[0],30,30);//left down
                    colorpoints[18] = getLedval(colorspaces[1],30,30);
                    colorpoints[17] = getLedval(colorspaces[2],30,30);
                    colorpoints[16] = getLedval(colorspaces[3],30,30);
                    colorpoints[15] = getLedval(colorspaces[4],30,30);
                    colorpoints[14] = getLedval(colorspaces[5],30,30);
                    colorpoints[13] = getLedval(colorspaces[6],30,30);
                    colorpoints[12] = getLedval(colorspaces[7],30,30);
                    colorpoints[11] = getLedval(colorspaces[8],30,30);
                    colorpoints[10] = getLedval(colorspaces[9],30,30);
                    colorpoints[9 ] = getLedval(colorspaces[10],30,30);
                    colorpoints[8 ] = getLedval(colorspaces[11],30,30);
                    colorpoints[7 ] = getLedval(colorspaces[12],30,30);
                    colorpoints[6 ] = getLedval(colorspaces[13],30,30);
                    colorpoints[5 ] = getLedval(colorspaces[14],30,30);
                    colorpoints[4 ] = getLedval(colorspaces[15],30,30);
                    colorpoints[3 ] = getLedval(colorspaces[16],30,30);
                    colorpoints[2 ] = getLedval(colorspaces[17],30,30);
                    colorpoints[1 ] = getLedval(colorspaces[18],30,30);
                    colorpoints[0 ] = getLedval(colorspaces[19],30,30);
                    colorpoints[29] = getLedval(colorspaces[20],30,30);
                    colorpoints[28] = getLedval(colorspaces[21],30,30);
                    colorpoints[27] = getLedval(colorspaces[22],30,30);
                    colorpoints[26] = getLedval(colorspaces[23],30,30);
                    colorpoints[25] = getLedval(colorspaces[24],30,30);
                    colorpoints[24] = getLedval(colorspaces[25],30,30);
                    colorpoints[23] = getLedval(colorspaces[26],30,30);
                    colorpoints[22] = getLedval(colorspaces[27],30,30);
                    colorpoints[21] = getLedval(colorspaces[28],30,30);
                    colorpoints[20] = getLedval(colorspaces[29],30,30);

                    /*
                    ByteBuffer byteBuffer = ByteBuffer.allocate(colorpoints.length*4+1);
                    IntBuffer intBuffer = byteBuffer.asIntBuffer();
                    intBuffer.put(colorpoints);
                    byteBuffer.put((byte)10);
                    bluetoothService.setwData(byteBuffer.array());
                    */
                    StringBuilder sendstr = new StringBuilder("");
                    for(int color : colorpoints){
                        //int a = color>>24;
                        int r = (color>>16)&0xff;
                        int g = (color>>8)&0xff;
                        int b = color&0xff;

                        sendstr.append(r);
                        sendstr.append("r");
                        sendstr.append(g);
                        sendstr.append("g");
                        sendstr.append(b);
                        sendstr.append("b");
                    }
                    sendstr.append("\r");
                    bluetoothService.setwData(sendstr.toString().getBytes());
//                    int r = (colorpoints[0]>>16)&0xff;
//                    int g = (colorpoints[0]>>8)&0xff;
//                    int b = colorpoints[0]&0xff;
//                     Log.d("lmpex","colorpoint0:"+colorpoints[0]+"r"+r+"g"+g+"b"+b);

                    // write bitmap to a file
//                    fos = new FileOutputStream(STORE_DIRECTORY + "/myscreen_" + IMAGES_PRODUCED + ".png");
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);


                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
            }
        }
    }

}
