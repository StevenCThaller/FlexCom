package com.flexcom.reading.flexcom;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements BluetoothCallback {

    //constants used for the glucose trend levels
    private static final String TREND_RISING = "Rising";
    private static final String TREND_FALLING = "Falling";
    private static final String TREND_STEADY = "Steady";

    //constants that deal with handling results from activities
    private static final int BLUETOOTH_CHECK = 0;
    private static final int BLUETOOTH_CHECK_FIRST_RUN = 1;
    private static final int TRANSMITTER_CHECK_FIRST_RUN = 2;

    //Shared Preferences to check if this is the first time the application is run
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor preferencesEditor;

    //booleans to hold status of bluetooth and transmitter states
    private boolean btEnabled;
    private boolean transmitterPaired;
    private boolean transmitterConnected;

    private Toolbar toolbar;
    private Bluetooth bluetooth;
    private GlucoseDatabase glucoseDatabase;

    //used to handle an array of old readings received from transmitter when that need to be added to the database
    private static int arraySize = 0;
    private static ArrayList oldReadings = new ArrayList();

    //used to hold the glucose values
    private static int lastGlucoseLevel, currentGlucoseLevel, averageGlucose, dailyHighGlucose, dailyLowGlucose = 0;
    private static String currentTrend = TREND_STEADY;
    private static int alertLow = 80;
    private static int alertHigh = 105;


    //initial creation of the application
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //make sure all the necessary permissions are enabled
        checkPermissions();

        //initialize and add the toolbar
        init();
        addToolbar();

        glucoseDatabase.readFromDatabase();

        //check if this is the first time running application
        if(firstRun()){
            initialSetup();
        }

        //set the trending level
        ((TextView) findViewById(R.id.tvTrendingGlucose)). setText(getText(R.string.trendingGlucose) + " ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        bluetooth.registerReceiver();
        checkConnections(); //checks if bluetooth is turned on and if a transmitter is connected

        //get the glucose level values
        currentGlucoseLevel = sharedPreferences.getInt("currentGlucoseLevel", 0);
        averageGlucose = sharedPreferences.getInt("averageGlucose", 0);
        dailyHighGlucose = sharedPreferences.getInt("dailyHighGlucose", 0);
        dailyLowGlucose = sharedPreferences.getInt("dailyLowGlucose", 0);

        //determine last glucose level color
        if(currentGlucoseLevel < alertLow){
            ((TextView) findViewById(R.id.tvCurrentGlucose)).setTextColor(Color.BLUE);
        }
        else if(currentGlucoseLevel > alertHigh){
            ((TextView) findViewById(R.id.tvCurrentGlucose)).setTextColor(Color.RED);
        }
        else{
            ((TextView) findViewById(R.id.tvCurrentGlucose)).setTextColor(Color.BLACK);
        }
        //Set the glucose values for each type (current, avg, etc)
        ((TextView) findViewById(R.id.tvCurrentGlucose)).setText(getText(R.string.currentGlucose) + " " + currentGlucoseLevel);
        ((TextView) findViewById(R.id.tvAverageGlucose)).setText(getText(R.string.averageGlucose) + " " + averageGlucose);
        ((TextView) findViewById(R.id.tvDailyHigh)).setText(getText(R.string.dailyHigh) + " " + dailyHighGlucose);
        ((TextView) findViewById(R.id.tvDailyLow)). setText(getText(R.string.dailyLow) + " " + dailyLowGlucose);
    }

    @Override
    protected void onPause() {
        super.onPause();
        bluetooth.unregisterReceiver();
    }

    //creates the toolbar menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.menuExit:
                System.exit(0);
                return true;

            case R.id.menuGraph:
                //TODO
                Toast.makeText(getBaseContext(), "Clicked on Graph", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.menuSettings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.menuRefresh:
                bluetooth.startScanning();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check which request we're responding to
        if (requestCode == BLUETOOTH_CHECK_FIRST_RUN) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                hideBluetoothConnectionError();
                buildWelcomeTransmitterDialog().show();
            }
            else if(resultCode == RESULT_CANCELED){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Welcome");
                builder.setMessage("You have chosen not to turn on your Bluetooth connection at this time, therefore the initial setup cannot proceed" +
                        "\n\nWould you like to cancel the initial setup and exit the application?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivityForResult(new Intent(bluetooth.getBluetoothAdapter().ACTION_REQUEST_ENABLE), BLUETOOTH_CHECK_FIRST_RUN);
                    }
                });
                builder.create().show();
            }
        }
        else if(requestCode == TRANSMITTER_CHECK_FIRST_RUN){
            if (resultCode == RESULT_OK) {
                buildWelcomeCompletedDialog().show();
            }
            else if(resultCode == RESULT_CANCELED){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Welcome");
                builder.setMessage("You have chosen not to turn on your pair a transmitter at this time, therefore the initial setup cannot proceed" +
                        "\n\nWould you like to cancel the initial setup and exit the application?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivityForResult(new Intent(getApplicationContext(), PairingActivity.class), TRANSMITTER_CHECK_FIRST_RUN);
                    }
                });
                builder.create().show();
            }
        }
    }

    /*
     ************************************************
     * This are the methods for the BluetoothCallback
     ************************************************
     */

    @Override
    public void onConnected(Bluetooth uart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                transmitterConnected = true;
                hideTransmitterConnectionError();
            }
        });
    }

    @Override
    public void onConnectFailed(Bluetooth uart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                transmitterConnected = false;
                showTransmitterConnectionError();
                bluetooth.startScanning();
            }
        });
    }

    @Override
    public void onDisconnected(Bluetooth uart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                transmitterConnected = false;
                showTransmitterConnectionError();
                bluetooth.startScanning();
            }
        });

    }

    @Override
    public void onReceive(Bluetooth uart, BluetoothGattCharacteristic rx) {
        String Tag = "onReceive Method";
        String str = rx.getStringValue(0);
        int reading = Integer.parseInt(str);
        ArrayList readings = new ArrayList();
        ArrayList dailyReadings = new ArrayList();
        Log.d(Tag, "The onReceive method received the following value -> " + reading);

        //transmitter is setup to send a num over 500 if an array of values need to be stored in the database
        if(reading > 1000){
            return;
        }
        if(reading > 500){
            //we have an array of missing glucose data
            //do this to discard old data after first pairing
            arraySize = reading - 500;

            Log.d(Tag, "Revceived the arry code. Number of old readings: " + arraySize);

            return;
        }
        if(arraySize > 0){
            arraySize--;
            oldReadings.add(reading);

            Log.d(Tag, "Added old reading to the oldReadings array." +
            "\nOld Reading is: " + reading +
            "\noldReading array size: " + oldReadings.size() +
            "\nold readings left: " + arraySize);
        }
        if(!oldReadings.isEmpty() && arraySize == 0){
            if(sharedPreferences.contains("firstReading")) {
                Log.d(Tag, "Sending the oldReading array to write to database. oldReading array size: " + oldReadings.size());

                glucoseDatabase.writeToDatabase(oldReadings);
                oldReadings.clear();
            }
            else{
                preferencesEditor.putString("firstReading", "complete");
                oldReadings.clear();
                Log.d("FIRSTREADING ->", "CLEARED ARRAY");
                return;
            }
        }
        else if(oldReadings.isEmpty()){
            Log.d(Tag, "Sending single reading to write to database.");
            glucoseDatabase.writeToDatabase(reading);
        }
        else{
            Log.d(Tag, "RETURNING");
            return;
        }

        //begin updating all the values
        Calendar todaysDate = Calendar.getInstance();
        todaysDate.setTime(Calendar.getInstance().getTime());

        Calendar beginDay = (Calendar) todaysDate.clone();
        beginDay.set(Calendar.DAY_OF_MONTH, todaysDate.getActualMinimum(Calendar.DAY_OF_MONTH));

        Calendar endDay = (Calendar) todaysDate.clone();
        endDay.set(Calendar.DAY_OF_MONTH, todaysDate.getActualMaximum(Calendar.DAY_OF_MONTH));

        Date begin = beginDay.getTime();
        Date end = endDay.getTime();

        readings = glucoseDatabase.readFromDatabase();
        dailyReadings = glucoseDatabase.readFromDatabase(begin, end);
        Collections.sort(dailyReadings);

        Log.d("Daily Readings Size -> ", String.valueOf(dailyReadings.size()) +
        "\nBeginDate -> " + begin.toString() +
        "\nEndDate -> " + end.toString());

        setCurrentGlucoseLevel(readings);
        setAverageGlucoseLevel(readings);
        setTrendingLevel();
        setDailyHighLevel(dailyReadings);
        setDailyLowLevel(dailyReadings);
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {

    }

    @Override
    public void onDeviceInfoAvailable() {

    }

    @Override
    public void onBluetoothNotEnabled() {
        btEnabled = false;
        transmitterConnected = false;
        bluetooth.setGattToNull();
        showBluetoothConnectionError();
        showTransmitterConnectionError();
    }

    @Override
    public void onBluetoothEnabled() {
        btEnabled = true;
        hideBluetoothConnectionError();
        bluetooth.startScanning();
    }

    /*
     *************************************************
     * These are functions pertaining to this activity
     *************************************************
     */
    public void setCurrentGlucoseLevel(ArrayList readings){
        lastGlucoseLevel = currentGlucoseLevel;
        currentGlucoseLevel = (int) readings.get(readings.size() - 1);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = findViewById(R.id.tvCurrentGlucose);
                if(currentGlucoseLevel < alertLow){
                    tv.setTextColor(Color.BLUE);
                }
                else if(currentGlucoseLevel > alertHigh){
                    tv.setTextColor(Color.RED);
                }
                else{
                    tv.setTextColor(Color.BLACK);
                }

                tv.setText(getText(R.string.currentGlucose) + " " + currentGlucoseLevel);
                preferencesEditor.putInt("currentGlucoseLevel", currentGlucoseLevel).commit();
            }
        });
    }

    public void setAverageGlucoseLevel(ArrayList readings){
        int totalValue = 0;

        for(int i = 0; i < readings.size(); i++){
           totalValue += (int) readings.get(i);
        }
        averageGlucose = totalValue / readings.size();

        runOnUiThread(new Runnable() {
        @Override
        public void run() {
            TextView tv = findViewById(R.id.tvAverageGlucose);
            tv.setText(getText(R.string.averageGlucose) + " " + averageGlucose);
            preferencesEditor.putInt("averageGlucose", averageGlucose).commit();
        }
        });


    }

    public void setDailyHighLevel(ArrayList readings){
        if(!readings.isEmpty()) {
            dailyHighGlucose = (int) readings.get(readings.size() - 1);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.tvDailyHigh);
                    tv.setText(getText(R.string.dailyHigh) + " " + dailyHighGlucose);
                    preferencesEditor.putInt("dailyHighGlucose", dailyHighGlucose).commit();
                }
            });
        }
    }

    public void setDailyLowLevel(ArrayList readings){

        if(!readings.isEmpty()) {
            dailyLowGlucose = (int) readings.get(0);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.tvDailyLow);
                    tv.setText(getText(R.string.dailyLow) + " " + dailyLowGlucose);
                    preferencesEditor.putInt("dailyLowGlucose", dailyLowGlucose).commit();
                }
            });
        }
    }

    public void setTrendingLevel(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = findViewById(R.id.tvTrendingGlucose);
                if(lastGlucoseLevel == currentGlucoseLevel){
                    currentTrend = TREND_STEADY;
                }
                else if(lastGlucoseLevel < currentGlucoseLevel){
                    currentTrend = TREND_RISING;
                }
                else if(lastGlucoseLevel > currentGlucoseLevel){
                    currentTrend = TREND_FALLING;
                }

                tv.setText(getText(R.string.trendingGlucose) + " " + currentTrend);
            }
        });
    }

    public int getCurrentGlucoseLevel(){
        TextView tv = findViewById(R.id.tvCurrentGlucose);
        String text = tv.getText().toString(); //get the TextView string
        int index = text.indexOf(":") + 2; //find the index of where the value begins. Adding two to account for the space before actual value
        return Integer.parseInt(text.substring(index));
    }

    public int getAverageGlucoseLevel(){
        TextView tv = findViewById(R.id.tvAverageGlucose);
        String text = tv.getText().toString(); //get the TextView string
        int index = text.indexOf(":") + 2; //find the index of where the value begins. Adding two to account for the space before actual value
        return Integer.parseInt(text.substring(index));
    }

    public int getDailyHighLevel(){
        TextView tv = findViewById(R.id.tvDailyHigh);
        String text = tv.getText().toString(); //get the TextView string
        int index = text.indexOf(":") + 2; //find the index of where the value begins. Adding two to account for the space before actual value
        return Integer.parseInt(text.substring(index));
    }

    public int getDailyLowLevel(){
        TextView tv = findViewById(R.id.tvDailyLow);
        String text = tv.getText().toString(); //get the TextView string
        int index = text.indexOf(":") + 2; //find the index of where the value begins. Adding two to account for the space before actual value
        return Integer.parseInt(text.substring(index));
    }



    private void showBluetoothConnectionError(){
        TextView tv = findViewById(R.id.btNotConnectedError);
        tv.setVisibility(TextView.VISIBLE);
    }

    private void showTransmitterConnectionError(){
        if(((TextView) findViewById(R.id.transmitterNotPairedError)).getVisibility() == TextView.VISIBLE){
            return;
        }
        TextView tv = findViewById(R.id.transmitterNotConnectedError);
        tv.setVisibility(TextView.VISIBLE);
    }

    private void showTransmitterPairedError(){
        if(((TextView) findViewById(R.id.transmitterNotConnectedError)).getVisibility() == TextView.VISIBLE){
            hideTransmitterConnectionError();
        }
        TextView tv = findViewById(R.id.transmitterNotPairedError);
        tv.setVisibility(TextView.VISIBLE);
    }

    private void hideBluetoothConnectionError(){
        TextView tv = findViewById(R.id.btNotConnectedError);
        tv.setVisibility(TextView.GONE);
    }

    private void hideTransmitterConnectionError(){
        TextView tv = findViewById(R.id.transmitterNotConnectedError);
        tv.setVisibility(TextView.GONE);
    }

    private void hideTransmitterPairedError(){
        TextView tv = findViewById(R.id.transmitterNotPairedError);
        tv.setVisibility(TextView.GONE);
    }

    private void checkConnections(){
        //check if Bluetooth is enabled
        if(bluetooth.getBluetoothAdapter().isEnabled()){
            btEnabled = true;
            hideBluetoothConnectionError();
        }
        else{
            btEnabled = false;
            showBluetoothConnectionError();
        }

        //check if a transmitter is paired
        if(sharedPreferences.contains(getString(R.string.keyPairedTransmitterName))){
            transmitterPaired = true;
            hideTransmitterPairedError();
        }
        else{
            transmitterPaired = false;
            showTransmitterPairedError();
        }

        //check if transmitter connected
         if(transmitterConnected || Bluetooth.transmitterConnected){
            transmitterConnected = true;
            hideTransmitterConnectionError();
            return;
        }
        if(transmitterPaired && btEnabled){
            showTransmitterConnectionError();
            bluetooth.startScanning();
        }
        else{
            transmitterConnected = false;
            showTransmitterConnectionError();
        }
    }

    private void addToolbar(){
        //setup the toolbar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setIcon(R.drawable.ic_action_name);
    }

    private void init() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferencesEditor = sharedPreferences.edit();
        bluetooth = new Bluetooth(this, this);
        glucoseDatabase = new GlucoseDatabase(this);

        //set the default values
        ((TextView) findViewById(R.id.tvCurrentGlucose)).setText(getText(R.string.currentGlucose) + " " + currentGlucoseLevel);
        ((TextView) findViewById(R.id.tvAverageGlucose)).setText(getText(R.string.averageGlucose) + " " + averageGlucose);
        ((TextView) findViewById(R.id.tvDailyHigh)).setText(getText(R.string.dailyHigh) + " " + dailyHighGlucose);
        ((TextView) findViewById(R.id.tvDailyLow)). setText(getText(R.string.dailyLow) + " " + dailyLowGlucose);
        ((TextView) findViewById(R.id.tvTrendingGlucose)).setText(getText(R.string.trendingGlucose) + " " + TREND_STEADY);
    }

    private boolean firstRun(){
        if(sharedPreferences.contains("firstRun")){
            return false;
        }
        preferencesEditor.putInt("currentGlucoseLevel", currentGlucoseLevel).commit();
        preferencesEditor.putInt("averageGlucose", averageGlucose).commit();
        preferencesEditor.putInt("dailyHighGlucose", dailyHighGlucose).commit();
        preferencesEditor.putInt("dailyLowGlucose", dailyLowGlucose).commit();

        return true;
    }

    private void checkPermissions() {
        int permissionsCheck = PermissionChecker.checkSelfPermission(this, "Manifest.permission.ACCESS_FINE_LOCATION");
        permissionsCheck += PermissionChecker.checkSelfPermission(this, "Manifest.permission.ACCESS_COARSE_LOCATION");

        if (permissionsCheck != 0) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        } else {
            Log.d(null, "No need to check permissions");
        }
    }


    /*
     ****************************************
     * This all deals with the initial setup
     ****************************************
     */

    private static final int WELCOME_INTRO = 0;
    private static final int WELCOME_BLUETOOTH = 1;
    private static final int WELCOME_TRANSMITTER = 2;
    private static final int WELCOME_COMPLETED = 3;

    public class dialogOnClickListener implements DialogInterface.OnClickListener{
        private int dialog;

        public dialogOnClickListener(int dialog){
            this.dialog = dialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (this.dialog){
                case WELCOME_INTRO:
                    if(bluetooth.getBluetoothAdapter().isEnabled()){
                        buildWelcomeTransmitterDialog().show();
                    }
                    else{
                        buildWelcomeBluetoothEnableDialog().show();
                    }
                    break;
                case WELCOME_BLUETOOTH:
                    startActivityForResult(new Intent(bluetooth.getBluetoothAdapter().ACTION_REQUEST_ENABLE), BLUETOOTH_CHECK_FIRST_RUN);
                    break;
                case WELCOME_TRANSMITTER:
                    startActivityForResult(new Intent(getApplicationContext(), PairingActivity.class), TRANSMITTER_CHECK_FIRST_RUN);
                    break;
                case WELCOME_COMPLETED:
                    preferencesEditor.putString("firstRun","Complete").commit();
                    transmitterConnected = true;
                    checkConnections();
                    break;
            }
        }
    }

    private void initialSetup(){
        //show the necessary error TextViews
        if(bluetooth.getBluetoothAdapter().isEnabled()){
            showTransmitterPairedError();
        }
        else{
            showTransmitterPairedError();
            showBluetoothConnectionError();
        }

        //add the shared preference that related to the paired transmitter
        preferencesEditor.putString(getString(R.string.keyPairedTransmitter), getString(R.string.transmitterNotPaired)).commit();

        //show the welcome dialog
        buildWelcomeDialog().show();
    }

    private AlertDialog buildWelcomeDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome");
        builder.setMessage("Welcome and thank you for downloading the Flexcom application." +
                "\n\nThe next steps will take you through the process of pairing a transmitter to the application to begin tracking your glucose levels" +
                "\n\nClick on Next to begin the process.");
        builder.setNeutralButton("Next", new dialogOnClickListener(WELCOME_INTRO));
        return builder.create();
    }

    private AlertDialog buildWelcomeBluetoothEnableDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome");
        builder.setMessage("Looks like we first need to enable your Bluetooth connection" +
                "\n\nClick Next to enable your Bluetooth connection");
        builder.setNeutralButton("Next", new dialogOnClickListener(WELCOME_BLUETOOTH));
        return builder.create();
    }

    private AlertDialog buildWelcomeTransmitterDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome");
        builder.setMessage("Great, Bluetooth is enabled. Now we need to pair a transmitter to the application." +
                "\n\nClick Next to search for new devices");
        builder.setNeutralButton("Next", new dialogOnClickListener(WELCOME_TRANSMITTER));
        return builder.create();
    }

    private AlertDialog buildWelcomeCompletedDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome");
        builder.setMessage("You are all set. You can now begin tracking your glucose levels.");
        builder.setNeutralButton("Finish", new dialogOnClickListener(WELCOME_COMPLETED));
        return builder.create();
    }
}
