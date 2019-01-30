package com.flexcom.reading.flexcom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class PairingActivity extends AppCompatActivity implements BluetoothCallback, AdapterView.OnItemClickListener {

    private static final int BLUETOOTH_CHECK = 0;

    private Toolbar toolbar;
    private Bluetooth bluetooth;
    private ListView listView;
    private ArrayAdapter<String> listAdapter;
    public static ArrayList<String> deviceInformation;
    public static ArrayList<BluetoothDevice> devices;

    //Shared Preferences to change preferences as needed
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor preferencesEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);
        bluetooth = new Bluetooth(this, this);

        //add the toolbar
        addToolbar();

        //initialize the shared preferences variables
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferencesEditor = sharedPreferences.edit();

        //Initialize arrays containing device information
        deviceInformation = new ArrayList<>();
        devices = new ArrayList<>();

        //get the list view from the layout file
        listView = (ListView) findViewById((R.id.tranmitterList));

        //initialize the array adapter needed for the list view
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceInformation);

        //set the list view on-click listener
        listView.setOnItemClickListener(this);

        //start scanning
        bluetooth.startScanning();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bluetooth.registerReceiver();

        if(!bluetooth.getBluetoothAdapter().isEnabled()){
            onBluetoothNotEnabled();
        }

        //get the TextView that shows the paired transmitter and update it
        TextView tv = findViewById(R.id.pairedTransmitter);
        String text = sharedPreferences.getString(getString(R.string.keyPairedTransmitterName), getString(R.string.transmitterNotPaired));
        tv.setText(text);
    }

    @Override
    protected void onPause() {
        super.onPause();
        bluetooth.unregisterReceiver();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BLUETOOTH_CHECK) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
    }

    /*
     *
     * THIS DEALS WITH THE BLUETOOTH CALLBACK
     *
     */

    @Override
    public void onConnected(Bluetooth uart) {
    }

    @Override
    public void onConnectFailed(Bluetooth uart) {

    }

    @Override
    public void onDisconnected(Bluetooth uart) {

    }

    @Override
    public void onReceive(Bluetooth uart, BluetoothGattCharacteristic rx) {
        Log.d("zzzz1234", "WHY AM I ENTERING HERE");
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        //add the device to the necessary arrays
        if(devices.isEmpty()) {
            deviceInformation.add("Name: " + device.getName() + "\nAddress: " + device.getAddress());
            devices.add(device);
        }
        else if(devices.contains("Name: " + device.getName() + "\nAddress: " + device.getAddress())){
            deviceInformation.add("Name: " + device.getName() + "\nAddress: " + device.getAddress());
            devices.add(device);
        }

        //set adapter to its respective list view
        listView.setAdapter(listAdapter);
    }

    @Override
    public void onDeviceInfoAvailable() {

    }

    @Override
    public void onBluetoothNotEnabled() {
        devices.clear();
        deviceInformation.clear();
        listAdapter.clear();
        listAdapter.notifyDataSetChanged();
        startActivityForResult(new Intent(bluetooth.getBluetoothAdapter().ACTION_REQUEST_ENABLE), BLUETOOTH_CHECK);
    }

    @Override
    public void onBluetoothEnabled() {

    }

    /*
     *
     * THIS DEALS WITH THE FOUND DEVICES BEING CLICKED ON
     *
     */

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //identify the device that was clicked
        BluetoothDevice device = devices.get(position);
        String deviceInfo = deviceInformation.get(position);

        //attempt to pair the transmitter
        pairTransmitter(device).show();

    }

    /*
     *
     * THESE ARE CLASS SPECIFIC METHODS
     *
     */

    private void addToolbar(){
        //setup the toolbar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_action_name);
    }

    private AlertDialog pairTransmitter(final BluetoothDevice device){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pair Transmitter");
        builder.setMessage("Would you like to pair the following transmitter:" +
                "\n\n" + device.getName());
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //update the shared preferences value
                preferencesEditor.putString(getString(R.string.keyPairedTransmitter), device.getAddress()).commit();
                preferencesEditor.putString(getString(R.string.keyPairedTransmitterName), device.getName()).commit();

                //update this activity to show new paired transmitter
                TextView tv = findViewById(R.id.pairedTransmitter);
                tv.setText(device.getName());

                //set result for choosing to pair transmitter
                setResult(RESULT_OK);
                finish();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        return builder.create();
    }
}
