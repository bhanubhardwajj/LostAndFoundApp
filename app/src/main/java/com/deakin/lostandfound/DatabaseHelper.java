package com.deakin.lostandfound;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQLite work for the Lost & Found app.
 *
 * Version history:
 *   v1 - original schema
 *   v2 - added category, image_path, timestamp  (Task 7.1)
 *   v3 - added latitude, longitude              (Task 9.1)
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "lost_and_found.db";
    private static final int    DB_VERSION = 3;

    public static final String TABLE_ITEMS  = "items";
    public static final String COL_ID          = "_id";
    public static final String COL_POST_TYPE   = "post_type";
    public static final String COL_NAME        = "name";
    public static final String COL_PHONE       = "phone";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_DATE        = "date";
    public static final String COL_LOCATION    = "location";
    public static final String COL_CATEGORY    = "category";
    public static final String COL_IMAGE_PATH  = "image_path";
    public static final String COL_TIMESTAMP   = "timestamp";
    public static final String COL_LATITUDE    = "latitude";   // NEW in v3
    public static final String COL_LONGITUDE   = "longitude";  // NEW in v3

    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_ITEMS + " (" +
                    COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_POST_TYPE   + " TEXT NOT NULL, " +
                    COL_NAME        + " TEXT NOT NULL, " +
                    COL_PHONE       + " TEXT, " +
                    COL_DESCRIPTION + " TEXT, " +
                    COL_DATE        + " TEXT, " +
                    COL_LOCATION    + " TEXT, " +
                    COL_CATEGORY    + " TEXT, " +
                    COL_IMAGE_PATH  + " TEXT, " +
                    COL_TIMESTAMP   + " INTEGER NOT NULL DEFAULT 0, " +
                    COL_LATITUDE    + " REAL NOT NULL DEFAULT 0, " +
                    COL_LONGITUDE   + " REAL NOT NULL DEFAULT 0" +
                    ")";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Task 7.1 additions
            db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COL_CATEGORY    + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COL_IMAGE_PATH  + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COL_TIMESTAMP   + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            // Task 9.1 additions — safe to run on both v1 and v2 databases
            db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COL_LATITUDE  + " REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COL_LONGITUDE + " REAL NOT NULL DEFAULT 0");
        }
    }

    // -----------------------------------------------------------------------
    //  CRUD helpers
    // -----------------------------------------------------------------------

    /** Insert a new advert. Returns the new row id, or -1 on failure. */
    public long insertItem(Item item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = buildContentValues(item);
        long id = db.insert(TABLE_ITEMS, null, cv);
        db.close();
        return id;
    }

    /**
     * Pull all adverts from the DB, newest first.
     *
     * @param categoryFilter "All" / null → no filter; otherwise exact match
     * @param search         free text match on name + description; null to skip
     */
    public List<Item> getAllItems(String categoryFilter, String search) {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();

        if (categoryFilter != null && !categoryFilter.isEmpty()
                && !categoryFilter.equalsIgnoreCase("All")) {
            where.append(COL_CATEGORY).append(" = ?");
            args.add(categoryFilter);
        }
        if (search != null && !search.trim().isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("(").append(COL_NAME).append(" LIKE ? OR ")
                 .append(COL_DESCRIPTION).append(" LIKE ?)");
            args.add("%" + search.trim() + "%");
            args.add("%" + search.trim() + "%");
        }

        Cursor c = db.query(
                TABLE_ITEMS, null,
                where.length() == 0 ? null : where.toString(),
                args.isEmpty()      ? null : args.toArray(new String[0]),
                null, null, COL_TIMESTAMP + " DESC");

        List<Item> items = cursorToList(c);
        c.close();
        db.close();
        return items;
    }

    /**
     * Returns all items that have a valid lat/lng coordinate stored.
     * Used by MapActivity to avoid placing a pin at 0,0 (Gulf of Guinea).
     */
    public List<Item> getItemsWithLocation() {
        SQLiteDatabase db = getReadableDatabase();
        // Exclude rows where both lat and lng are 0 (default / not set)
        Cursor c = db.query(
                TABLE_ITEMS, null,
                "NOT (" + COL_LATITUDE + " = 0 AND " + COL_LONGITUDE + " = 0)",
                null, null, null, COL_TIMESTAMP + " DESC");
        List<Item> items = cursorToList(c);
        c.close();
        db.close();
        return items;
    }

    /** Single item by primary key. */
    public Item getItem(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_ITEMS, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        Item item = null;
        if (c.moveToFirst()) item = rowToItem(c);
        c.close();
        db.close();
        return item;
    }

    /** Delete an advert. Returns the number of rows removed (0 or 1). */
    public int deleteItem(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_ITEMS, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    // -----------------------------------------------------------------------
    //  Geo helper
    // -----------------------------------------------------------------------

    /**
     * Haversine formula — returns the great-circle distance in kilometres
     * between two lat/lng points.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private ContentValues buildContentValues(Item item) {
        ContentValues cv = new ContentValues();
        cv.put(COL_POST_TYPE,   item.getPostType());
        cv.put(COL_NAME,        item.getName());
        cv.put(COL_PHONE,       item.getPhone());
        cv.put(COL_DESCRIPTION, item.getDescription());
        cv.put(COL_DATE,        item.getDate());
        cv.put(COL_LOCATION,    item.getLocation());
        cv.put(COL_CATEGORY,    item.getCategory());
        cv.put(COL_IMAGE_PATH,  item.getImagePath());
        cv.put(COL_TIMESTAMP,   item.getTimestamp());
        cv.put(COL_LATITUDE,    item.getLatitude());
        cv.put(COL_LONGITUDE,   item.getLongitude());
        return cv;
    }

    private Item rowToItem(Cursor c) {
        Item item = new Item();
        item.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
        item.setPostType(c.getString(c.getColumnIndexOrThrow(COL_POST_TYPE)));
        item.setName(c.getString(c.getColumnIndexOrThrow(COL_NAME)));
        item.setPhone(c.getString(c.getColumnIndexOrThrow(COL_PHONE)));
        item.setDescription(c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION)));
        item.setDate(c.getString(c.getColumnIndexOrThrow(COL_DATE)));
        item.setLocation(c.getString(c.getColumnIndexOrThrow(COL_LOCATION)));
        item.setCategory(c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)));
        item.setImagePath(c.getString(c.getColumnIndexOrThrow(COL_IMAGE_PATH)));
        item.setTimestamp(c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP)));
        item.setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LATITUDE)));
        item.setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LONGITUDE)));
        return item;
    }

    private List<Item> cursorToList(Cursor c) {
        List<Item> items = new ArrayList<>();
        while (c.moveToNext()) items.add(rowToItem(c));
        return items;
    }
}
