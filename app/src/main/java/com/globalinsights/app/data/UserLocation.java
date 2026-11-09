package com.globalinsights.app.data;

public class UserLocation {

    private double latitude;
    private double longitude;
    private long timestamp;
    private boolean sharing;
    private String countryCode;

    // Public no-arg constructor required by Cloud Firestore
    public UserLocation() {
    }

    public UserLocation(double latitude, double longitude, long timestamp, boolean sharing, String countryCode) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.sharing = sharing;
        this.countryCode = countryCode;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSharing() {
        return sharing;
    }

    public void setSharing(boolean sharing) {
        this.sharing = sharing;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}