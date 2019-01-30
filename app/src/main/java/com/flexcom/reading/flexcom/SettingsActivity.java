package com.flexcom.reading.flexcom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;


public class SettingsActivity extends AppCompatActivity implements BluetoothCallback {

    private Toolbar toolbar;
    private Bluetooth bluetooth;

    //Shared Preferences to modify preferences as needed
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor preferencesEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //add the toolbar
        addToolbar();

        //used for managing the bluetooth actions
        bluetooth = new Bluetooth(this, this);

        //initialize the shared preferences variables
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferencesEditor = sharedPreferences.edit();

        //disable certain preferences if Bluetooth is OFF
        if(!bluetooth.getBluetoothAdapter().isEnabled()){
            Preference preference = SettingsFragment.getPreferencesManager().findPreference(getString(R.string.keyPairedTransmitter));
            preference.setEnabled(false);
            preference.setSummary(R.string.btNotConnectedError);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bluetooth.registerReceiver();

        //check the pair transmitter preference to set the correct summary
        Preference preference = SettingsFragment.getPreferencesManager().findPreference(getString(R.string.keyPairedTransmitter));
        String summary = "Currently Paired Transmitter Name: \n" + sharedPreferences.getString(getString(R.string.keyPairedTransmitterName), getString(R.string.transmitterNotPaired));
        preference.setSummary(summary);

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
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

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

    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {

    }

    @Override
    public void onDeviceInfoAvailable() {

    }

    @Override
    public void onBluetoothNotEnabled() {
        SettingsFragment.getPreferencesManager()
                .findPreference(getString(R.string.keyPairedTransmitter))
                .setEnabled(false);
        SettingsFragment.getPreferencesManager()
                .findPreference(getString(R.string.keyPairedTransmitter))
                .setSummary(R.string.btNotConnectedError);
    }

    @Override
    public void onBluetoothEnabled() {
        SettingsFragment.getPreferencesManager()
                .findPreference(getString(R.string.keyPairedTransmitter))
                .setEnabled(true);
        SettingsFragment.getPreferencesManager()
                .findPreference(getString(R.string.keyPairedTransmitter))
                .setSummary(SettingsFragment.getPreferencesManager()
                        .getSharedPreferences()
                        .getString(SettingsFragment.getPreferencesManager().findPreference(getString(R.string.keyPairedTransmitterName)).getKey(), getString(R.string.transmitterNotPaired)));
    }

    private void addToolbar(){
        //setup the toolbar
        toolbar = (Toolbar) findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_action_name);
    }
}
