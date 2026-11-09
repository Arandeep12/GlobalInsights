package com.globalinsights.app.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class CountryBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "CountryReceiver";
    public static final String ACTION_REPORT_SAVED = "com.globalinsights.app.ACTION_REPORT_SAVED";
    public static final String ACTION_SYNC_COMPLETED = "com.globalinsights.app.ACTION_SYNC_COMPLETED";
    public static final String ACTION_SYNC_FAILED = "com.globalinsights.app.ACTION_SYNC_FAILED";

    public interface SyncStatusListener {
        void onSyncFinished();
    }

    private static SyncStatusListener syncStatusListener;

    public static void setSyncStatusListener(SyncStatusListener listener) {
        syncStatusListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        String countryName = intent.getStringExtra("country_name");
        String countryCode = intent.getStringExtra("country_code");
        String population = intent.getStringExtra("population");
        String gdp = intent.getStringExtra("gdp");

        if (countryName == null) countryName = "Country";
        if (countryCode == null) countryCode = "GLOBAL";
        if (population == null) population = "N/A";
        if (gdp == null) gdp = "N/A";

        Log.d(TAG, "Received Broadcast: " + action + " for " + countryName + " (" + countryCode + ")");

        final String finalCountryName = countryName;

        if (ACTION_REPORT_SAVED.equals(action)) {
            NotificationManagerHelper.showReportSavedNotification(context, finalCountryName, countryCode, population, gdp);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Report saved for " + finalCountryName + " (Notification Triggered)", Toast.LENGTH_SHORT).show()
            );

        } else if (ACTION_SYNC_COMPLETED.equals(action)) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "World Bank Sync Completed for " + finalCountryName, Toast.LENGTH_SHORT).show()
            );
            if (syncStatusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> syncStatusListener.onSyncFinished());
            }

        } else if (ACTION_SYNC_FAILED.equals(action)) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "World Bank Sync Failed for " + finalCountryName, Toast.LENGTH_SHORT).show()
            );
            if (syncStatusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> syncStatusListener.onSyncFinished());
            }
        }
    }
}