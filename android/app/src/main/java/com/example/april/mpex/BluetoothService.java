package com.example.april.mpex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothService {
    // Debugging
    private static final String TAG = "lmpex";

    // Intent request code
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // RFCOMM Protocol
    private static final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter btAdapter;

    private Activity mActivity;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private int mState;


    private static final int STATE_NONE = 0; // we're doing nothing
    private static final int STATE_LISTEN = 1; // now listening for incoming
    // connections
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing
    // connection
    private static final int STATE_CONNECTED = 3; // now connected to a remote
    // device
    byte wData[];
    // Constructors
    public BluetoothService(Activity ac) {
        mActivity = ac;

        // BluetoothAdapter ���
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Check the Bluetooth support
     *
     * @return boolean
     */
    public boolean getDeviceState() {
        Log.i(TAG, "Check the Bluetooth support");

        if (btAdapter == null) {
            Log.d(TAG, "Bluetooth is not available");

            return false;

        } else {
            Log.d(TAG, "Bluetooth is available");

            return true;
        }
    }

    /**
     * Check the enabled Bluetooth
     */
    public void enableBluetooth() {
        Log.i(TAG, "Check the enabled Bluetooth");

        if (btAdapter.isEnabled()) {
            //if  Bluetooth is On
            Log.d(TAG, "Bluetooth Enable Now");

            // Next Step
            scanDevice();
        } else {
            // if Bluetooth is Off
            Log.d(TAG, "Bluetooth Enable Request");

            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(i, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Available device search
     */
    public void scanDevice() {
        Log.d(TAG, "Scan Device");

        Intent serverIntent = new Intent(mActivity, DeviceListActivity.class);
        mActivity.startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    /**
     * after scanning and get device info
     *
     * @param data
     */
    public void getDeviceInfo(Intent data) {
        // Get the device MAC address
        String address = data.getExtras().getString(
                DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        // BluetoothDevice device = btAdapter.getRemoteDevice(address);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        Log.d(TAG, "Get Device Info \n" + "address : " + address);

        connect(device);
    }

    // Bluetooth 상태 set
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    // Bluetooth 상태 get
    public synchronized int getState() {
        return mState;
    }

    //Connect, Connected 쓰레드 연결된 것 끊고초기화.
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread == null) {

        } else {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    // Connect, ConnectedThread 초기화. device의 모든 연결을 제거한다.
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread == null) {

            } else {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);

        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    // mConnectedThread와 mConnectThread를 모두 초기화 후 mConnectedThread를 새로 만듬.
    //mConnectedThread.start()로 run 메소드를 실행시킴.
    public synchronized void connected(BluetoothSocket socket,
                                       BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread == null) {

        } else {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    // 모든 thread stop
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }


    public void write(byte[] out) { // Create temporary object
        ConnectedThread r; // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        } // Perform the write unsynchronized r.write(out); }
    }

    public void setwData(byte[] wdata){
        wData = wdata;
    }
    // 연결 실패
    private void connectionFailed() {
        setState(STATE_LISTEN);
    }

    // 연결을 잃었을때
    private void connectionLost() {
        setState(STATE_LISTEN);

    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            /*
             * / // Get a BluetoothSocket to connect with the given
             * BluetoothDevice try { // MY_UUID is the app's UUID string, also
             * used by the server // code tmp =
             * device.createRfcommSocketToServiceRecord(MY_UUID);
             *
             * try { Method m = device.getClass().getMethod(
             * "createInsecureRfcommSocket", new Class[] { int.class }); try {
             * tmp = (BluetoothSocket) m.invoke(device, 15); } catch
             * (IllegalArgumentException e) { // TODO Auto-generated catch block
             * e.printStackTrace(); } catch (IllegalAccessException e) { // TODO
             * Auto-generated catch block e.printStackTrace(); } catch
             * (InvocationTargetException e) { // TODO Auto-generated catch
             * block e.printStackTrace(); }
             *
             * } catch (NoSuchMethodException e) { // TODO Auto-generated catch
             * block e.printStackTrace(); } } catch (IOException e) { } /
             */

            //디바이스 정보를 얻어 bluetoothsocket을 생성.
            //mmSocket
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            //연결 시도 전, 기기 검색을 중지하는 부분.
            //기기 검색을 계속 하면 연결 속도가 느려진다.
            btAdapter.cancelDiscovery();

            // BluetoothSocket 연결 시도.
            try {
                // BluetoothSocket
                mmSocket.connect();
                Log.d(TAG, "Connect Success");

            } catch (IOException e) {
                connectionFailed(); //연결 실패
                Log.d(TAG, "Connect Fail");

                // socket을 닫는다
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG,
                            "unable to close() socket during connection failure",
                            e2);
                }
                //BluetoothService의 Connected , Connect Thread 모두 초기화
                BluetoothService.this.start();
                return;
            }

            //ConnectThread 클래스를 reset
            //mConnectThread
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }
            //ConncectThread를 시작
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    //ConnectThread의 다음 단계.
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //BluetoothSocket의 inputstream과 outputstream을 얻는다.

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //블루투스 서비스의 실질적인 동작부분.
        //
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int readBufferPosition = 0;
            boolean received = true;
            // Keep listening to the InputStream while connected

            while (true) {
                if(received){
                    write(wData);//Capture service가 준 데이터를 쓴다.
                    received = false;
                }
                try {
                    //InputStream으로부터 값을 받는 부분.
                    int bytesAvailable = mmInStream.available();
                    if(bytesAvailable > 0){
                        //Log.d(TAG,"bytesavailable"+bytesAvailable);
                    }

                    if(bytesAvailable>0){
                        byte[] PacketBytes = new byte[bytesAvailable];
                        mmInStream.read(PacketBytes);
                        for(int i = 0;i<bytesAvailable;i++){
                            byte b = PacketBytes[i];
                            if(b == '\n'){
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(buffer,0,encodedBytes,0,encodedBytes.length);
                                final String data = new String(encodedBytes,"US-ASCII");
                                readBufferPosition = 0;
                                Log.d(TAG,"returned data:"+data);
                                received = true;
                            }else{
                                buffer[readBufferPosition++] = b;
                            }
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer
         *            The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                //OutputStream으로 값을 쓰는 부분.
                mmOutStream.write(buffer);
                Log.d(TAG,"dataSended"+String.valueOf(buffer.length));
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}