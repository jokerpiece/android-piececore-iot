/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.jokerpiece.iotcore;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLEConnector extends Service
{
    private final static String TAG = BLEConnector.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private ArrayList<String> sensorData;
    private Handler mHandler;
    private boolean mScanning;
    private boolean hasDetectedJPsensor = false;
    private int scanRetryCount = 0;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


//    //Broadcast Tag
    public final static String ACTION_SCAN_STATE_FOUND =
            "com.example.bluetooth.le.ACTION_SCAN_STATE_FOUND";
    public final static String ACTION_SCAN_STATE_NOT_FOUND =
            "com.example.bluetooth.le.ACTION_SCAN_STATE_NOT_FOUND";

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SettingClass.HEART_RATE_MEASUREMENT);


    // Device scan callback. back to main thread
    public BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                //When a device has been discovered, add to list
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
                {
                    BaseSensorMainActivity.mLeDeviceListAdapter.addDevice(device);
                    BaseSensorMainActivity.mLeDeviceListAdapter.notifyDataSetChanged();

                    String deviceName = device.getName();
                    if(deviceName!=null&&deviceName.contains("JPSns")&&!hasDetectedJPsensor)
                    {
                        hasDetectedJPsensor = true;
                        Log.d("Ble_Device_Name:", device.getName().toString() );
                        Log.d("Ble_Device_Address:",device.getAddress().toString());
                        SettingClass.JPSENSOR_ADDRESS = device.getAddress();
//                        //Connect to JPSensor
//                        final boolean result = connect(SettingClass.JPSENSOR_ADDRESS);
//                        Log.d("Connect_result:", "Connect request result=" + result);
                        broadcastUpdate(ACTION_SCAN_STATE_FOUND);
                        Log.d("bluetooth_callback:","Found the JPSensor, Address:"+SettingClass.JPSENSOR_ADDRESS);
                    }
                }

            };

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    // 使用BluetoothGattCallback類別去連接，偵測，測試ble device
    // 實作方法
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {

            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                hasDetectedJPsensor = true;
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);//自己宣告的方法，可丟入廣播標籤
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                hasDetectedJPsensor = false;
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
            else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

        }



    };

    //Broadcast the message to broadcastreceiver (call by BluetoothGattCallback and only send a tag)
    private void broadcastUpdate(final String action)
    {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    //Analyze the Cha data
    //Broadcast the message to broadcast receiver (call by BluetoothGattCallback, send a tag and a Chr UUID)
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic)
    {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        // If it is heart rate sensor, change encoding type
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid()))
        {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0)
            {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            }
            else
            {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        }
        else//Other device
        {
            // For all other profiles, writes the data formatted in HEX.
            // get Value of characteristic uuid
            sensorData = new ArrayList<>();

            final byte[] data = characteristic.getValue();
            //String parameter = characteristic.getDescriptor(characteristic.getUuid()).toString();
            if (data != null && data.length > 0)
            {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                {
                    sensorData.add(Integer.toString(byteChar));
                    stringBuilder.append("  "+byteChar);
                }

                    // stringBuilder.append(String.format("%02X ", byteChar));
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                intent.putExtra("sensor_data", sensorData);

                Log.d("Sensor_data:",stringBuilder.toString());
            }
        }
        sendBroadcast(intent);//broadcast

    }


    //binder
    public class LocalBinder extends Binder
    {
        BLEConnector getService() {
            return BLEConnector.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent)
    {
        //new a Handler to run scan thread
        mHandler = new Handler();
        scanRetryCount=0;

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }



    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    //Initialize bluetooth device
    public boolean initialize()
    {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    //Connect GATT server
    public boolean connect(final String address)
    {

        if (mBluetoothAdapter == null || address == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null)
        {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect())
            {
                mConnectionState = STATE_CONNECTING;

                return true;
            }
            else
            {
                return false;
            }
        }


        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null)
        {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);

        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect()
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close()
    {
        if (mBluetoothGatt == null)
        {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    //Get Cha data
    public void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (characteristic!=null)
        {
            mBluetoothGatt.readCharacteristic(characteristic);//Back value with true or false，response in callback's onCharacteristicRead method
        }

    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        if(enabled)
        {
            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

            // Characteristic の Notification 有効化，enable the sensor!
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SettingClass.CLIENT_CHARACTERISTIC_CONFIG));//固定値
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        else
        {
            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        }


        // This is specific to Heart Rate Measurement.
        /*
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid()))
        {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        */
    }


    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    //Get service in bluetooth
    public List<BluetoothGattService> getSupportedGattServices()
    {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    //scan BLE device by using recursion
    public void scanLeDevice(final boolean enable)
    {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(scanRetryCount < SettingClass.SCAN_RETRY_COUNT && !hasDetectedJPsensor)
                    {
                        scanRetryCount++;
                        //keep scan
                        Log.d("StartLEScan:", Integer.toString(scanRetryCount));
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        hasDetectedJPsensor = false;
                        scanLeDevice(true);
                    }
                    else
                    {
                        if(!hasDetectedJPsensor){
                            broadcastUpdate(ACTION_SCAN_STATE_NOT_FOUND);
                            Log.d("bluetooth_callback:","device not found");
                        }
                        //end the scan
                        scanRetryCount = 0;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        hasDetectedJPsensor = false;
                    }

                }
            }, SettingClass.SCAN_PERIOD);


            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {

            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }
}
