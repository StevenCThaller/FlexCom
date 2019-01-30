package com.flexcom.reading.flexcom;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Bluetooth extends BluetoothGattCallback implements BluetoothAdapter.LeScanCallback {
    //For logging purposes
    private static String TAG = "Bluetooth";

    //We need a context for this class
    private static Context context;

    //IntentFilter that will be used for broadcast changes
    private static IntentFilter filter;

    //handler to stop scanning after a set time period
    private static Handler mHandler;
    private static final int SCAN_PERIOD = 10000;

    //used in logic to connect the paired transmitter
    private static SharedPreferences sharedPreferences;
    private static String savedDeviceAddress;

    public static boolean transmitterConnected;

    // UUIDs for UAT service and associated characteristics.
    private static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    private static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // BTLE state
    private static BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private static BluetoothGatt gatt = null;
    private static BluetoothGattCharacteristic tx = null;
    private static BluetoothGattCharacteristic rx = null;

    //The interface to call the callbacks
    private static BluetoothCallback callback;

    //BroadcastReceiver to check for Bluetooth connection changes
    private static BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                if(adapter.getState() == adapter.STATE_OFF){
                    callback.onBluetoothNotEnabled();
                }
                else if(adapter.getState() == adapter.STATE_ON){
                    callback.onBluetoothEnabled();
                }
            }
        }
    };

    public static boolean receiverRegistered = false;

    //constructor
    public Bluetooth(Context context, BluetoothCallback callback){
        this.context = context;
        this.callback = callback;
        this.gatt = null;
        this.tx = null;
        this.rx = null;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        savedDeviceAddress = sharedPreferences.getString("pairedTransmitter", "No transmitter paired");
    }

    //callback for BluetoothAdapter.LeScanCallback
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        savedDeviceAddress = sharedPreferences.getString("pairedTransmitter", "No transmitter paired");
        //check to see if the device is one that is already paired and connect if it is
        if(device.getAddress().equals(savedDeviceAddress)){
            // Connect to the device.
            // Control flow will now go to the callback functions when BTLE events occur.
            Log.d(TAG, "onLeScan: Found an already paired device");
            stopScanning();
            if(gatt == null) {
                gatt = device.connectGatt(context, true, this);
            }
            else{
                gatt.connect();
            }
            return;
        }

        // Check if the device has the UART service.
        if (parseUUIDs(scanRecord).contains(UART_UUID)) {
            Log.d(TAG, "onLeScan: Found UART device -> " + device.getAddress());
            //notify callback that a device was found
            callback.onDeviceFound(device);
        }
    }

    // Callbacks for BluetoothGattCallback
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            Log.d(TAG, "onConnectionStateChange: BluetoothGatt -> STATE_CONNECTED");
            // Discover services.
            if (!gatt.discoverServices()) {
                Log.d(TAG, "onConnectionStateChange: BluetoothGatt -> Services were not discovered");
                // Error starting service discovery.
                connectFailure();
            }
        }
        else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            Log.d(TAG, "onConnectionStateChange: BluetoothGatt -> STATE_DISCONNECTED");
            // Disconnected, notify callbacks of disconnection.
            rx = null;
            tx = null;
            callback.onDisconnected(this);
        }
        else {
            Log.d(TAG, "onConnectionStateChange: BluetoothGatt -> Connection state changed.  New state: " + newState);
        }
    }

    // Called when services have been discovered on the remote device.
    // It seems to be necessary to wait for this discovery to occur before
    // manipulating any services or characteristics.
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onServicesDiscovered: Service discovery completed!");
        }
        else {
            Log.d(TAG, "onServicesDiscovered: Service discovery failed with status: " + status);
            connectFailure();
            return;
        }
        // Save reference to each characteristic.
        tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
        rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
        // Setup notifications on RX characteristic changes (i.e. data received).
        // First call setCharacteristicNotification to enable notification.
        if (!gatt.setCharacteristicNotification(rx, true)) {
            Log.d(TAG, "onServicesDiscovered: Couldn't set notifications for RX characteristic!");
            // Stop if the characteristic notification setup failed.
            connectFailure();
            return;
        }
        // Next update the RX characteristic's client descriptor to enable notifications.
        if (rx.getDescriptor(CLIENT_UUID) != null) {
            BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(desc)) {
                Log.d(TAG, "onServicesDiscovered: Couldn't write RX client descriptor value!");
                // Stop if the client descriptor could not be written.
                connectFailure();
                return;
            }
        }
        else {
            Log.d(TAG, "onServicesDiscovered: Couldn't get RX client descriptor!");
            connectFailure();
            return;
        }
        transmitterConnected = true;
        callback.onConnected(this);
    }

    // Called when a remote characteristic changes (like the RX characteristic).
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        //TODO: THIS IS WHERE I WILL WORK ON RECEIVING DATA FROM THE TRANSMITTER
        Log.d(TAG, "onCharacteristicChanged: Received -> " + characteristic.getStringValue(0));
        callback.onReceive(this, characteristic);
    }

    @Override
    public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        callback.onDeviceInfoAvailable();
    }

    // Disconnect to a device if currently connected.
    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
        }
        gatt = null;
        tx = null;
        rx = null;
        unregisterReceiver();
    }

    // Notify callbacks of connection failure, and reset connection state.
    private void connectFailure() {
        rx = null;
        tx = null;
        callback.onConnectFailed(this);
    }

    public BluetoothAdapter getBluetoothAdapter(){
        return adapter;
    }

    public BluetoothGatt getGatt() {
        return gatt;
    }

    public void registerReceiver(){
        filter = new IntentFilter(new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        context.registerReceiver(receiver, filter);
    }

    public void startScanning(){
        //stop scan first in case there is another scan in progress
        adapter.stopLeScan(this);
        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanning();
            }
        }, SCAN_PERIOD);
        adapter.startLeScan(this);
    }

    public void stopScanning(){
        adapter.stopLeScan(this);
    }

    public void setContext(Context context){
        this.context = context;
    }

    public void unregisterReceiver(){
        context.unregisterReceiver(receiver);
    }

    public void setGattToNull(){
        gatt = null;
    }

    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }

}

