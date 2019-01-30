package com.flexcom.reading.flexcom;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;


public class SettingsFragment extends PreferenceFragmentCompat {

    private static PreferenceManager preferenceManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        preferenceManager = getPreferenceManager();
        addPreferencesFromResource(R.xml.preferences);

    }

    public static PreferenceManager getPreferencesManager(){
        return preferenceManager;
    }
}
