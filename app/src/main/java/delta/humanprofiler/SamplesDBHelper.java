package delta.humanprofiler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Delta on 29/11/2015.
 */
public class SamplesDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String SAMPLES_TABLE_NAME = "dictionary";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final int MAX_CATEGORY_LENGTH = 64;

    private static SamplesDBHelper mInstance = null;
    private ArrayList<ChangeWatcher> mChangeWatchers;

    private SamplesDBHelper(Context context) {
        super(context, context.getString(R.string.db_name) + "." + SAMPLES_TABLE_NAME, null, DATABASE_VERSION);
        mChangeWatchers = new ArrayList<ChangeWatcher>();
    }

    public static synchronized SamplesDBHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SamplesDBHelper(context);
        }
        return mInstance;
    }

    static public boolean isValidUserCategory(Context context, String category) {
        return category.length() > 0 &&
                !category.equalsIgnoreCase(context.getString(R.string.do_not_disturb_category));
    }

    private void onChange() {
        synchronized (mChangeWatchers) {
            ArrayList<ChangeWatcher> newWatchers = new ArrayList<ChangeWatcher>();
            for (ChangeWatcher watcher : mChangeWatchers) {
                if (watcher.onChange()) {
                    newWatchers.add(watcher);
                }
            }
            mChangeWatchers = newWatchers;
        }
    }

    void registerWatcher(ChangeWatcher watcher) {
        synchronized (mChangeWatchers) {
            mChangeWatchers.add(watcher);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SAMPLES_TABLE_NAME + " (" +
                KEY_CATEGORY + " VARCHAR(" + MAX_CATEGORY_LENGTH + ") NOT NULL, " +
                KEY_TIMESTAMP + " INTEGER NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    void insertSample(CharSequence category) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues sample = new ContentValues();
        sample.put(KEY_CATEGORY, normalizeCategoryName(category));
        sample.put(KEY_TIMESTAMP, Calendar.getInstance().getTimeInMillis());
        db.insertOrThrow(SAMPLES_TABLE_NAME, null, sample);
        db.close();
        onChange();
    }

    long numSamples() {
        SQLiteDatabase db = getReadableDatabase();
        long count = DatabaseUtils.queryNumEntries(db, SAMPLES_TABLE_NAME);
        db.close();
        return count;
    }

    List<String> getCategories(Context context, boolean onlyUserDefined) {
        String[] columns = {KEY_CATEGORY};
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(true, SAMPLES_TABLE_NAME, columns, null, null, null, null, KEY_CATEGORY, null);
        ArrayList<String> categories = new ArrayList<String>(cursor.getCount());
        if (cursor.moveToFirst()) {
            do {
                String category = cursor.getString(0);
                if (!onlyUserDefined || isValidUserCategory(context, category)) {
                    categories.add(category);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return categories;
    }

    Map<String, Integer> getCategoriesDistribution() {
        SQLiteDatabase db = getReadableDatabase();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<String, Integer>();
        Cursor cursor = db.rawQuery(
                "SELECT " + KEY_CATEGORY + ", COUNT(*) FROM " + SAMPLES_TABLE_NAME + " GROUP BY " +
                        KEY_CATEGORY + " ORDER BY " + KEY_CATEGORY, null);
        if (cursor.moveToFirst()) {
            do {
                result.put(cursor.getString(0), cursor.getInt(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    void renameCategory(CharSequence oldCategory, CharSequence newCategory) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_CATEGORY, normalizeCategoryName(newCategory));
        db.update(SAMPLES_TABLE_NAME, values, KEY_CATEGORY + "=?",
                new String[]{oldCategory.toString()});
        db.close();
        onChange();
    }

    void changeLast(CharSequence newCategory) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor =
                db.rawQuery("SELECT MAX(" + KEY_TIMESTAMP + ") FROM " + SAMPLES_TABLE_NAME, null);
        if (!cursor.moveToFirst()) {
            Log.e(this.getClass().getCanonicalName(), "attempted changeLast on empty database.");
            return;
        }
        Long timestamp = cursor.getLong(0);
        cursor.close();

        ContentValues values = new ContentValues();
        values.put(KEY_CATEGORY, normalizeCategoryName(newCategory));
        db.update(SAMPLES_TABLE_NAME, values, KEY_TIMESTAMP + "=?",
                new String[]{timestamp.toString()});
        db.close();
        onChange();
    }

    void clearData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(SAMPLES_TABLE_NAME, null, null);
        db.close();
        onChange();
    }

    String normalizeCategoryName(CharSequence name) {
        return WordUtils.capitalize(
                WordUtils.wrap(name.toString().trim(), MAX_CATEGORY_LENGTH, "\n", true)
                        .split("\n")[0]);
    }

    public interface ChangeWatcher {
        boolean onChange();
    }
}
