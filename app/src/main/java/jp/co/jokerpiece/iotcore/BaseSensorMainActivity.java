package jp.co.jokerpiece.iotcore;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class BaseSensorMainActivity extends AppCompatActivity
{
    public Context context;
    public static LeDeviceListAdapter mLeDeviceListAdapter;
    public ArrayList<String> sensorDataArrayList;//saving values of sensor

    public ArrayList<BluetoothDevice> mLeDevices;


    public static BluetoothAdapter mBluetoothAdapter;
    public Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;

    private int scanRetryCount;

    //GGATconnector
    private final static String TAG = BaseSensorMainActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    public int mConnectionState;
    public String mDeviceName;
    public String mDeviceAddress;
    public BLEConnector mBLEConnector;
    public ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    public boolean mConnected = false;
    public BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    public ArrayList<String> analyzedSensorData;// aDistance, aBattery, aTemp, aHumidity
    public int totalUpdateCount;
    public int totalDistance;
    public int effectCount;
    public int resultBatteryLife = 0;
    public int resultHeight = 0;
    public static boolean canScanBleDevice = false;

    // Code to manage Service lifecycle.
    protected final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            SharedPreferences personalData = getSharedPreferences("PersonalData",MODE_PRIVATE);
            SettingClass.MIN_DISTANCE = personalData.getInt("rice_min_height",50);
            SettingClass.MAX_DISTANCE = personalData.getInt("rice_min_height",1000);
            mBLEConnector = ((BLEConnector.LocalBinder) service).getService();

            if (!mBLEConnector.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Start Scan
            mLeDeviceListAdapter = new LeDeviceListAdapter();
//            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            mBLEConnector.scanLeDevice(true);
            // Automatically connects to the device upon successful start-up initialization.
            //mBLEConnector.connect(mLeDeviceListAdapter.getDevice(0).getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEConnector = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        sensorDataArrayList = new ArrayList<>();
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d("Inherit","Inhert excute");

        //String
        mDeviceName = EXTRAS_DEVICE_NAME;
        //String
        mDeviceAddress = EXTRAS_DEVICE_ADDRESS;

        //Start Service
        Intent gattServiceIntent = new Intent(this, BLEConnector.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled())
        {
            canScanBleDevice = false;
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else
        {
            canScanBleDevice = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            //Ble is off
            Toast.makeText(context,"米ライフアプリを使用する為、Bluetooth機能をオンにして下さい。", Toast.LENGTH_LONG).show();
            return;
        }
        else
        {
            //BLe is on
            canScanBleDevice = true;
        }
//        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBLEConnector = null;
    }

    //show current connecting state
    protected void updateConnectionState(final String connecttionState)
    {
        Toast.makeText(this, connecttionState, Toast.LENGTH_SHORT).show();
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    protected void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
        {
            return;
        }

        //Initialize the variables
        String uuid = null;
        String unknownServiceString = "Unknown service";
        String unknownCharaString = "Unknown characteristic";

        //Service Data HashMap: save the key&value for each service
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        //Characteristic Data HashMap: save the key&value for each characteristic of each service
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();

        //2D ArrayList: save characteristics id for each service
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices)
        {
            //Save Current Service's key&value data to a new HashMap
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            //get service's uuid
            uuid = gattService.getUuid().toString();

            //將抓到的uuid與sampleGattAttributes裡的uuid對照，查詢此uuid的服務名稱
            currentServiceData.put(
                    LIST_NAME, SettingClass.lookup(uuid, unknownServiceString));
            //Put discovered Service's UUID
            currentServiceData.put(LIST_UUID, uuid);
            //put current service data to hash map
            gattServiceData.add(currentServiceData);

            //new characteristic hash map with each service loop
            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();

            //??get current Service's characteristic ID and save it to list
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
            {
                charas.add(gattCharacteristic);

                HashMap<String, String> currentCharaData = new HashMap<String, String>();

                uuid = gattCharacteristic.getUuid().toString();

                currentCharaData.put(
                        LIST_NAME, SettingClass.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);

                gattCharacteristicGroupData.add(currentCharaData);


            }

            mGattCharacteristics.add(charas);

            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

    }

    //Ask data, group is index of Service UUID, child is index of Char UUID
    protected void getData(int group, int child)
    {
        if (mGattCharacteristics != null)
        {
            final BluetoothGattCharacteristic characteristic =
                    mGattCharacteristics.get(group).get(child);

            final int charaProp = characteristic.getProperties();

            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0)
            {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
                if (mNotifyCharacteristic != null)
                {
                    mBLEConnector.setCharacteristicNotification(mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                //Get Char data!!
                mBLEConnector.readCharacteristic(characteristic);

            }

            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
            {
                mNotifyCharacteristic = characteristic;
                mBLEConnector.setCharacteristicNotification(characteristic, true);
            }

        }

    }

    //Calculate the datas of sensor
    public void getAnalyzedSensorData()
    {
        if(sensorDataArrayList.size()!=0 && sensorDataArrayList!=null)
        {
            analyzedSensorData = new ArrayList<>();
            int distance1 = Integer.parseInt(sensorDataArrayList.get(0));
            int distance2 = Integer.parseInt(sensorDataArrayList.get(1));
            int battery = Integer.parseInt(sensorDataArrayList.get(2));
            int temp = Integer.parseInt(sensorDataArrayList.get(3));
            int humidity = Integer.parseInt(sensorDataArrayList.get(4));

            Log.d("distance1:", Integer.toString(distance1));
            Log.d("distance2:", Integer.toString(distance2));

            int aDistance = distance1 * 256 + Math.abs(distance2);
            int aBattery = battery;
            int aTemp = temp * 165 / 256 - 40;
            int aHumidity = Math.abs(humidity * 100 / 256);
            int aMinDistance = SettingClass.MIN_DISTANCE;
            int aMaxDistance = SettingClass.MAX_DISTANCE;

            analyzedSensorData.add(Integer.toString(aDistance));
            analyzedSensorData.add(Integer.toString(aBattery));
            analyzedSensorData.add(Integer.toString(aTemp));
            analyzedSensorData.add(Integer.toString(aHumidity));
            analyzedSensorData.add(Integer.toString(aMinDistance));
            analyzedSensorData.add(Integer.toString(aMaxDistance));
        }
        else
        {
            Log.d("Error:","sensorDataArrayList is null or size is 0");
        }

    }

    //IntentFilter is used by BroadcastReceiver
    protected static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEConnector.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEConnector.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEConnector.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEConnector.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLEConnector.ACTION_SCAN_STATE_NOT_FOUND);
        intentFilter.addAction(BLEConnector.ACTION_SCAN_STATE_FOUND);
        return intentFilter;
    }

    //Prevent back key to work
    @Override
    public void onBackPressed() {

    }

    // Inner Class Adapter for holding devices found through scanning.
    public class LeDeviceListAdapter extends BaseAdapter {


        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();

        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            return null;
        }
    }


}
