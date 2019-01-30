package com.flexcom.reading.flexcom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by dj781d on 4/7/2018.
 */

public interface BluetoothCallback {
    public abstract void onConnected(Bluetooth uart);
    public abstract void onConnectFailed(Bluetooth uart);
    public abstract void onDisconnected(Bluetooth uart);
    public abstract void onReceive(Bluetooth uart, BluetoothGattCharacteristic rx);
    public abstract void onDeviceFound(BluetoothDevice device);
    public abstract void onDeviceInfoAvailable();
    public abstract void onBluetoothNotEnabled();
    public abstract void onBluetoothEnabled();
}
