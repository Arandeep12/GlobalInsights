package com.globalinsights.app.data;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorldBankDataService extends Service {

    private static final String TAG = "WorldBankDataService";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String inputCode = (intent != null && intent.hasExtra("country_code"))
                ? intent.getStringExtra("country_code")
                : "IND";

        String inputName = (intent != null && intent.hasExtra("country_name"))
                ? intent.getStringExtra("country_name")
                : "India";

        final String code = (inputCode != null) ? inputCode : "IND";
        final String name = (inputName != null) ? inputName : "India";

        Log.d(TAG, "Starting World Bank background data sync for " + name + " (" + code + ")");

        executorService.execute(() -> {
            boolean isSuccess = false;
            String popFormatted = "N/A";
            String gdpFormatted = "N/A";

            try {
                String popUrl = "https://api.worldbank.org/v2/country/" + code + "/indicator/SP.POP.TOTL?format=json&mrnev=1";
                String gdpUrl = "https://api.worldbank.org/v2/country/" + code + "/indicator/NY.GDP.MKTP.CD?format=json&mrnev=1";

                String popJson = makeHttpGetRequest(popUrl);
                String gdpJson = makeHttpGetRequest(gdpUrl);

                if (popJson != null || gdpJson != null) {
                    double popVal = parseIndicatorValue(popJson);
                    double gdpVal = parseIndicatorValue(gdpJson);

                    popFormatted = formatPopulation(popVal);
                    gdpFormatted = formatGdp(gdpVal);
                    isSuccess = true;

                    Log.d(TAG, "Sync complete for " + name + " - Pop: " + popFormatted + ", GDP: " + gdpFormatted);

                    // 1. Direct System Notification Dispatch (Works even if Activity is closed/backgrounded)
                    NotificationManagerHelper.showMarketSyncNotification(this, name, code, popFormatted, gdpFormatted);

                    // 2. Dispatch internal app broadcast for active UI listeners
                    Intent broadcastIntent = new Intent(CountryBroadcastReceiver.ACTION_SYNC_COMPLETED);
                    broadcastIntent.setPackage(getPackageName());
                    broadcastIntent.putExtra("country_name", name);
                    broadcastIntent.putExtra("country_code", code);
                    broadcastIntent.putExtra("population", popFormatted);
                    broadcastIntent.putExtra("gdp", gdpFormatted);
                    sendBroadcast(broadcastIntent);
                } else {
                    Log.e(TAG, "Sync failed: World Bank HTTP requests returned null for " + code);
                    handleSyncFailure(name, code);
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during background World Bank data sync", e);
                handleSyncFailure(name, code);
            } finally {
                // Guaranteed service shutdown
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    private void handleSyncFailure(String name, String code) {
        // Direct Failure Notification Dispatch
        NotificationManagerHelper.showMarketSyncFailureNotification(this, name, code);

        // Broadcast failure event to UI
        Intent broadcastIntent = new Intent(CountryBroadcastReceiver.ACTION_SYNC_FAILED);
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra("country_name", name);
        broadcastIntent.putExtra("country_code", code);
        sendBroadcast(broadcastIntent);
    }

    private double parseIndicatorValue(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return -1;
        try {
            JSONArray rootArray = new JSONArray(jsonResponse);
            if (rootArray.length() >= 2 && !rootArray.isNull(1)) {
                JSONArray dataArray = rootArray.getJSONArray(1);
                if (dataArray.length() > 0 && !dataArray.isNull(0)) {
                    JSONObject dataObj = dataArray.getJSONObject(0);
                    if (!dataObj.isNull("value")) {
                        return dataObj.getDouble("value");
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error in service", e);
        }
        return -1;
    }

    private String formatPopulation(double value) {
        if (value < 0) return "Data Not Available";
        long count = Math.round(value);
        return NumberFormat.getInstance(Locale.US).format(count);
    }

    private String formatGdp(double value) {
        if (value < 0) return "Data Not Available";
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        currencyFormat.setMaximumFractionDigits(0);
        String rawFormatted = currencyFormat.format(value);

        String shorthand = "";
        if (value >= 1e12) {
            shorthand = String.format(Locale.US, " (~$%.2f Trillion)", value / 1e12);
        } else if (value >= 1e9) {
            shorthand = String.format(Locale.US, " (~$%.2f Billion)", value / 1e9);
        } else if (value >= 1e6) {
            shorthand = String.format(Locale.US, " (~$%.2f Million)", value / 1e6);
        }
        return rawFormatted + shorthand;
    }

    private String makeHttpGetRequest(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000); // 15s connection timeout
            connection.setReadTimeout(15000);    // 15s read timeout
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                StringBuilder builder = new StringBuilder();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                return builder.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP GET request error in service", e);
        } finally {
            if (connection != null) connection.disconnect();
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}