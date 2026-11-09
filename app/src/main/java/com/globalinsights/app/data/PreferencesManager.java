package com.globalinsights.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class PreferencesManager {

    private static final String PREF_NAME = "global_insights_prefs";
    private static final String KEY_PREFERRED_COUNTRY_CODE = "pref_country_code";
    private static final String KEY_PREFERRED_COUNTRY_NAME = "pref_country_name";
    private static final String KEY_NIGHT_MODE = "pref_night_mode";

    private final SharedPreferences preferences;

    public PreferencesManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setPreferredCountry(String code, String name) {
        if (code != null && name != null) {
            preferences.edit()
                    .putString(KEY_PREFERRED_COUNTRY_CODE, code)
                    .putString(KEY_PREFERRED_COUNTRY_NAME, name)
                    .apply();
        }
    }

    public String getPreferredCountryCode() {
        return preferences.getString(KEY_PREFERRED_COUNTRY_CODE, "IND");
    }

    public String getPreferredCountryName() {
        return preferences.getString(KEY_PREFERRED_COUNTRY_NAME, "India");
    }

    public void setNightMode(int mode) {
        preferences.edit().putInt(KEY_NIGHT_MODE, mode).apply();
    }

    public int getNightMode() {
        return preferences.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}