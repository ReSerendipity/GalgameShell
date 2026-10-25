package android.os;
public class BatteryManager {
    public static final int BATTERY_PROPERTY_CURRENT_NOW = 0;
    public static final int BATTERY_PROPERTY_VOLTAGE_NOW = 1;
    public static final String EXTRA_VOLTAGE = "voltage";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_STATUS = "status";
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_FULL = 5;
    public long getLongProperty(int id) { return 0L; }
}
