package delta.humanprofiler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created by Delta on 17/12/2015.
 */

public class ScheduleDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String SCHEDULE_TABLE_NAME = "BlackoutPeriods";
    private static final String KEY_START = "start";
    private static final String KEY_END = "end";
    private static final String KEY_ID = "_id";

    private static ScheduleDBHelper mInstance = null;

    private ScheduleDBHelper(Context context) {
        super(context, context.getString(R.string.db_name) + "." + SCHEDULE_TABLE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized ScheduleDBHelper getInstance(Context context){
        if(mInstance == null)
        {
            mInstance = new ScheduleDBHelper(context);
        }
        return mInstance;
    }

    static long timestampFromHourAndMinute(int hourOfDay, int minute) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }

    static Pair<Integer, Integer> hourAndMinuteFromTimestamp(long timestamp) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.setTimeInMillis(timestamp);
        return Pair.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SCHEDULE_TABLE_NAME + " (" +
                KEY_START + " INTEGER NOT NULL, " +
                KEY_END + " INTEGER NOT NULL, " +
                "_id INTEGER PRIMARY KEY);");
        ContentValues defaultValue = new ContentValues();
        defaultValue.put(KEY_START, timestampFromHourAndMinute(7, 0));
        defaultValue.put(KEY_END, timestampFromHourAndMinute(23, 0));
        db.insert(SCHEDULE_TABLE_NAME, null, defaultValue);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    synchronized IntervalsCursor getIntervalsCursor() {
        IntervalsCursor cursor = new IntervalsCursor(getReadableDatabase().query(
                SCHEDULE_TABLE_NAME, new String[]{KEY_ID, KEY_START, KEY_END}, null, null, null,
                null, KEY_START));
        if (!cursor.cursor().moveToFirst()) {
            Log.wtf(getClass().getCanonicalName(), "Unable to reset the cursor.");
        }
        return cursor;
    }

    boolean deleteInterval(long id) {
        SQLiteDatabase db = getWritableDatabase();
        if (db.delete(SCHEDULE_TABLE_NAME, KEY_ID + "=?", new String[]{Long.toString(id)}) == 0) {
            Log.e(getClass().getCanonicalName(),
                    "Could not find interval with id " + id + " in the database.");
            return false;
        }
        db.close();
        return true;
    }

    synchronized boolean addInterval(Range<Long> interval) {
        RangeSet<Long> data = TreeRangeSet.create();
        data.add(interval);

        IntervalsCursor cursor = getIntervalsCursor();
        while (cursor.isValid()) {
            data.add(cursor.range());
            cursor.advance();
        }
        cursor.cursor().close();

        SQLiteDatabase db = getWritableDatabase();
        db.delete(SCHEDULE_TABLE_NAME, null, null);
        ContentValues row = new ContentValues();
        for (Range<Long> r : data.asRanges()) {
            row.put(KEY_START, r.lowerEndpoint());
            row.put(KEY_END, r.upperEndpoint());
            long rowId = db.insert(SCHEDULE_TABLE_NAME, null, row);
            if (rowId < 0) {
                Log.e(getClass().getCanonicalName(), "Failed to insert row " + row.toString());
            } else {
                Log.v(getClass().getCanonicalName(), "Added row " + rowId + ": " + row.toString());
            }
            row.clear();
        }
        return true;
    }

    static class IntervalsCursor {
        private Cursor mCursor;

        IntervalsCursor(Cursor cursor) {
            mCursor = cursor;
        }

        public Cursor cursor() {
            return mCursor;
        }

        public Long id() {
            return mCursor.getLong(mCursor.getColumnIndex(KEY_ID));
        }

        public Range<Long> range() {
            return Range.closedOpen(
                    mCursor.getLong(mCursor.getColumnIndex(KEY_START)),
                    mCursor.getLong(mCursor.getColumnIndex(KEY_END)));
        }

        public boolean isValid() {
            return !mCursor.isAfterLast();
        }

        public boolean advance() {
            return mCursor.moveToNext();
        }
    }
}
