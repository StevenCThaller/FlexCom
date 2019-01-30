package com.flexcom.reading.flexcom;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class GlucoseDatabase {
    private static final String DATABASE_NAME = "glucosedata.db";
    private SQLiteDatabase database;
    private String query;
    private Context context;

    public GlucoseDatabase(Context context) {
        this.context = context;
        database = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS GlucoseData (time DATETIME DEFAULT CURRENT_TIMESTAMP, reading INTEGER)");
        query = "";
    }

    public void writeToDatabase(int reading){
        query = "INSERT into GlucoseData (time, reading) VALUES (datetime(), " + "\'" + reading + "')";
        database.execSQL(query);
        readFromDatabase();
    }

    public void writeToDatabase(int reading, String time){
        query = "INSERT into GlucoseData (time, reading) VALUES ('" + time + "\', \'" + reading + "')";
        database.execSQL(query);
    }

    public void writeToDatabase(ArrayList oldReadings){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Calendar adjustedTime = Calendar.getInstance();
        adjustedTime.setTime(Calendar.getInstance().getTime());
        int totalSeconds = oldReadings.size() * 10;

        for(int i = 0; i < oldReadings.size(); i++, totalSeconds = totalSeconds - 10 ) {
            int reading = (int) oldReadings.get(i);
            adjustedTime.add(Calendar.SECOND, totalSeconds * (-1));
            Date date = adjustedTime.getTime();
            String formattedDate = df.format(date);
            writeToDatabase(reading, formattedDate);
        }
    }

    public ArrayList readFromDatabase(){
        ArrayList readings = new ArrayList();
        Cursor cursor = database.rawQuery("SELECT * FROM GlucoseData", null);
        cursor.moveToFirst();

        for(int i = 0; i < cursor.getCount(); i++){
            int value = Integer.parseInt(cursor.getString(1));
            readings.add(value);
            Timestamp time = Timestamp.valueOf(cursor.getString(0));
            cursor.moveToNext();
            Log.d("DATABASE_VALUE ->", time + " --- " + value);
        }
        return readings;
    }

    public ArrayList readFromDatabase(Date beginDate, Date endDate){
        ArrayList readings = new ArrayList();
        Cursor cursor = database.rawQuery("SELECT * FROM GlucoseData", null);

        cursor.moveToFirst();



        for(int i = 0; i < cursor.getCount(); i++){
            boolean validDate = true;
            int reading = Integer.parseInt(cursor.getString(1));
            Timestamp time = Timestamp.valueOf(cursor.getString(0));

            if(beginDate != null){
                if(time.before(beginDate)){
                    validDate = false;
                }
            }

            if(endDate != null){
                if(time.after(endDate)){
                    validDate = false;
                }
            }

            if(validDate){
                readings.add(reading);
            }

            cursor.moveToNext();
        }
        return readings;
    }
}
