package com.globalinsights.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "global_insights.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_FAVORITES = "favorites";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "country_name";
    public static final String COLUMN_CODE = "country_code";
    public static final String COLUMN_POPULATION = "population";
    public static final String COLUMN_GDP = "gdp";
    public static final String COLUMN_ADDED_AT = "added_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_FAVORITES_TABLE = "CREATE TABLE " + TABLE_FAVORITES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT NOT NULL, " +
                COLUMN_CODE + " TEXT NOT NULL UNIQUE, " +
                COLUMN_POPULATION + " TEXT NOT NULL, " +
                COLUMN_GDP + " TEXT NOT NULL, " +
                COLUMN_ADDED_AT + " INTEGER NOT NULL)";
        db.execSQL(CREATE_FAVORITES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        onCreate(db);
    }

    public boolean addFavorite(String name, String code, String population, String gdp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_CODE, code);
        values.put(COLUMN_POPULATION, population);
        values.put(COLUMN_GDP, gdp);
        values.put(COLUMN_ADDED_AT, System.currentTimeMillis());

        long result = db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public boolean removeFavorite(String code) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_FAVORITES, COLUMN_CODE + " = ?", new String[]{code});
        return rows > 0;
    }

    public boolean isFavorite(String code) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{COLUMN_CODE},
                COLUMN_CODE + " = ?", new String[]{code}, null, null, null);
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) cursor.close();
        return exists;
    }

    public List<FavoriteCountry> getAllFavorites() {
        List<FavoriteCountry> favorites = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, null, null, null, null, null, COLUMN_ADDED_AT + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                String code = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CODE));
                String population = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_POPULATION));
                String gdp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GDP));
                favorites.add(new FavoriteCountry(id, name, code, population, gdp));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return favorites;
    }

    public static class FavoriteCountry {
        public final long id;
        public final String name;
        public final String code;
        public final String population;
        public final String gdp;

        public FavoriteCountry(long id, String name, String code, String population, String gdp) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.population = population;
            this.gdp = gdp;
        }
    }
}