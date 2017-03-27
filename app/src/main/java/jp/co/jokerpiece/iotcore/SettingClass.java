package jp.co.jokerpiece.iotcore;

/**
 * Created by Sou on 16/4/26.
 */

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SettingClass {
    //Assign scan period
    public static final long SCAN_PERIOD = 10000;
    public static final long ACCESSING_DATA_PERIOD = 500;
    //Assign scan retry count
    public static final int SCAN_RETRY_COUNT = 5;
    public static int DATA_DETECT_COUNT = 10;
    //Assign scan device
    public static String JPSENSOR_ADDRESS ="";
    //
    public static int MIN_DISTANCE = 50;
    public static int MAX_DISTANCE = 1000;

    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    //CHARACTERISTICを設定する固定値
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static String JPSENSOR_SERVICE_UUID ="78636976-0217-46c4-ae05-0b988b11da48";
    public static String JPSENSOR_CHARACTORISTIC_UUID ="b2e9046f-566c-4434-bb2c-3916d647b1ea";
    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        //JPSENSOR
        attributes.put("78636976-0217-46C4-AE05-0B988B11DA48","JPSensor service");
        attributes.put("B2E9046F-566C-4434-BB2C-3916D647B1EA","JPSensor characteristic");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }

}
