package com.globalinsights.app.data;

public class Note {

    private String id;
    private String title;
    private String content;
    private String summary;
    private String countryCode;
    private long timestamp;

    // Public no-argument constructor required by Cloud Firestore
    public Note() {
    }

    public Note(String id, String title, String content, String summary, String countryCode, long timestamp) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.countryCode = countryCode;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}