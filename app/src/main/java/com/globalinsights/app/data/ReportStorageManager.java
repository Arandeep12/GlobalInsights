package com.globalinsights.app.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportStorageManager {

    private static final String TAG = "ReportStorageManager";
    private static final String REPORTS_FILE = "saved_country_reports.json";

    public static class CountryReport {
        public final String id;
        public final String countryName;
        public final String countryCode;
        public final String populationText;
        public final String gdpText;
        public final String dataYear;
        public final String savedAtDate;

        public CountryReport(String id, String countryName, String countryCode, String populationText, String gdpText, String dataYear, String savedAtDate) {
            this.id = id;
            this.countryName = countryName;
            this.countryCode = countryCode;
            this.populationText = populationText;
            this.gdpText = gdpText;
            this.dataYear = dataYear;
            this.savedAtDate = savedAtDate;
        }
    }

    public static boolean saveReport(Context context, String countryName, String countryCode, String population, String gdp, String dataYear) {
        try {
            List<CountryReport> reports = getAllReports(context);
            String id = countryCode + "_" + System.currentTimeMillis();
            String date = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(new Date());

            CountryReport newReport = new CountryReport(id, countryName, countryCode, population, gdp, dataYear, date);
            reports.add(0, newReport); // prepend newest report

            JSONArray jsonArray = new JSONArray();
            for (CountryReport report : reports) {
                JSONObject obj = new JSONObject();
                obj.put("id", report.id);
                obj.put("countryName", report.countryName);
                obj.put("countryCode", report.countryCode);
                obj.put("populationText", report.populationText);
                obj.put("gdpText", report.gdpText);
                obj.put("dataYear", report.dataYear);
                obj.put("savedAtDate", report.savedAtDate);
                jsonArray.put(obj);
            }

            FileOutputStream fos = context.openFileOutput(REPORTS_FILE, Context.MODE_PRIVATE);
            fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();

            Log.d(TAG, "Report saved to internal storage for " + countryName + " (" + countryCode + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving report to internal storage", e);
            return false;
        }
    }

    public static List<CountryReport> getAllReports(Context context) {
        List<CountryReport> reports = new ArrayList<>();
        File file = new File(context.getFilesDir(), REPORTS_FILE);
        if (!file.exists()) {
            Log.d(TAG, "Internal storage report file does not exist yet.");
            return reports;
        }

        try {
            FileInputStream fis = context.openFileInput(REPORTS_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            fis.close();

            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                reports.add(new CountryReport(
                        obj.optString("id"),
                        obj.optString("countryName"),
                        obj.optString("countryCode"),
                        obj.optString("populationText"),
                        obj.optString("gdpText"),
                        obj.optString("dataYear"),
                        obj.optString("savedAtDate")
                ));
            }
            Log.d(TAG, "Loaded " + reports.size() + " reports from internal storage.");
        } catch (Exception e) {
            Log.e(TAG, "Error reading reports from internal storage", e);
        }
        return reports;
    }

    public static boolean deleteReport(Context context, String reportId) {
        try {
            List<CountryReport> reports = getAllReports(context);
            List<CountryReport> updated = new ArrayList<>();
            for (CountryReport report : reports) {
                if (!report.id.equals(reportId)) {
                    updated.add(report);
                }
            }

            JSONArray jsonArray = new JSONArray();
            for (CountryReport report : updated) {
                JSONObject obj = new JSONObject();
                obj.put("id", report.id);
                obj.put("countryName", report.countryName);
                obj.put("countryCode", report.countryCode);
                obj.put("populationText", report.populationText);
                obj.put("gdpText", report.gdpText);
                obj.put("dataYear", report.dataYear);
                obj.put("savedAtDate", report.savedAtDate);
                jsonArray.put(obj);
            }

            FileOutputStream fos = context.openFileOutput(REPORTS_FILE, Context.MODE_PRIVATE);
            fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();

            Log.d(TAG, "Deleted report ID " + reportId + " from internal storage.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting report from internal storage", e);
            return false;
        }
    }
}