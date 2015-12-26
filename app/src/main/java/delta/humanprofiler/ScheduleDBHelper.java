package delta.humanprofiler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.Range;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;

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
    private List<Pair<Long, Range<Long>>> mIntervals = null;

    public static synchronized ScheduleDBHelper getInstance(Context context){
        if(mInstance == null)
        {
            mInstance = new ScheduleDBHelper(context);
        }
        return mInstance;
    }

    private ScheduleDBHelper(Context context) {
        super(context, context.getString(R.string.db_name) + "." + SCHEDULE_TABLE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SCHEDULE_TABLE_NAME + " (" +
                KEY_START + " INTEGER NOT NULL, " +
                KEY_END + " INTEGER NOT NULL, " +
                "_id INTEGER PRIMARY KEY);");
        ContentValues defaultValue = new ContentValues();
        defaultValue.put(KEY_START, timeFromHourAndMinute(7, 0));
        defaultValue.put(KEY_END, timeFromHourAndMinute(23, 0));
        db.insert(SCHEDULE_TABLE_NAME, null, defaultValue);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    static long timeFromHourAndMinute(int hourOfDay, int minute) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }

    static Pair<Integer, Integer> hourAndMinuteFromTime(long time) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.setTimeInMillis(time);
        return Pair.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    static class IntervalsCursor {
        IntervalsCursor(Cursor cursor) {
            mCursor = cursor;
        }

        public Cursor cursor() {
            return mCursor;
        }

        public Long id() {
            return mCursor.getLong(mCursor.getColumnIndex(KEY_ID));
        }

        public Long startTime() {
            return mCursor.getLong(mCursor.getColumnIndex(KEY_START));
        }

        public Long endTime() {
            return mCursor.getLong(mCursor.getColumnIndex(KEY_END));
        }

        private Cursor mCursor;
    }

    IntervalsCursor getIntervalsCursor() {
        IntervalsCursor cursor = new IntervalsCursor(getReadableDatabase().query(
                SCHEDULE_TABLE_NAME, new String[]{KEY_ID, KEY_START, KEY_END}, null, null, null,
                null, KEY_START));
        if (cursor.cursor().moveToFirst()) {
            do {
                Log.v(getClass().getCanonicalName(),
                        String.format("id: %d, start: %tR, end: %tR.", cursor.id(),
                                cursor.startTime(), cursor.endTime()));
            } while (cursor.cursor().moveToNext());
            if (!cursor.cursor().moveToFirst()) {
                Log.wtf(getClass().getCanonicalName(), "Unable to reset the cursor.");
            }
        }
        return cursor;
    }

    private static int addIntervalToList(List<Pair<Long, Range<Long>>> list,
                                   long id, long begin, long end) {
        if (begin < end) {
            list.add(Pair.of(id, Range.between(begin, end)));
            return 1;
        } else if (begin > end) {
            // Split into 2 well ordered intervals.
            list.add(Pair.of(id,
                    Range.between(begin, timeFromHourAndMinute(24, 0))));
            list.add(0, Pair.of(id,
                    Range.between(timeFromHourAndMinute(0, 0), end)));
            return 2;
        } else {
            Log.e(ScheduleDBHelper.class.getCanonicalName(),
                    "Interval " + id + " is made of a single point " + begin);
        }
        return 0;
    }

    private synchronized List<Pair<Long, Range<Long>>> getSortedIntervals() {
        if (mIntervals != null) {
            if (areIntersecting(mIntervals)) {
                Log.e(getClass().getCanonicalName(), "Intervals start in intersecting state");
                SQLiteDatabase db = getWritableDatabase();
                db.delete(SCHEDULE_TABLE_NAME, null, null);
                db.close();
            } else {
                return mIntervals;
            }
        }
        IntervalsCursor cursor = getIntervalsCursor();
        mIntervals = new ArrayList<Pair<Long, Range<Long>>>(cursor.cursor().getCount());
        Long splitInterval = null;
        if (cursor.cursor().moveToFirst()) {
            do {
                long id = cursor.id();
                if (addIntervalToList(mIntervals, id, cursor.startTime(), cursor.endTime()) > 1) {
                    if (splitInterval != null) {
                        Log.e(getClass().getCanonicalName(),
                                "Multiple split intervals: " + splitInterval + " and " + id);
                    }
                    splitInterval = id;
                }
            } while (cursor.cursor().moveToNext());
        }
        cursor.cursor().close();

        sortIntervals(mIntervals);
        if (splitInterval != null &&
                (mIntervals.get(0).getKey().equals(splitInterval) ||
                        mIntervals.get(mIntervals.size() - 1).getKey().equals(splitInterval))) {
            Log.e(getClass().getCanonicalName(),
                    "Split interval not sorted correctly: " + mIntervals.toString());
        }
        return getSortedIntervals();
    }

    private static boolean areIntersecting(List<Pair<Long, Range<Long>>> sortedIntervals) {
        Range<Long> prevRange = null;
        for (Pair<Long, Range<Long>> interval : sortedIntervals) {
            if (prevRange != null && prevRange.isOverlappedBy(interval.getValue())) {
                Log.v(ScheduleDBHelper.class.getCanonicalName(),
                        "Overlapping interval " + interval.toString());
                return true;
            }
            prevRange = interval.getValue();
        }
        return false;
    }

    boolean deleteInterval(long id) {
        boolean found = false;
        if (mIntervals != null) {
            for (int i = 0; i < mIntervals.size(); i++) {
                if (mIntervals.get(i).getKey().equals(id)) {
                    mIntervals.remove(i);
                    // Special handling for split interval.
                    if (i == 0 && !mIntervals.isEmpty() &&
                            mIntervals.get(mIntervals.size() - 1).getKey().equals(id)) {
                        mIntervals.remove(mIntervals.size() - 1);
                    }
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            Log.e(getClass().getCanonicalName(),
                    "Could not find interval with id " + id + " in " + mIntervals.toString());
        }
        SQLiteDatabase db = getWritableDatabase();
        if (db.delete(SCHEDULE_TABLE_NAME, KEY_ID + "=?", new String[]{Long.toString(id)}) == 0) {
            Log.e(getClass().getCanonicalName(),
                    "Could not find interval with id " + id + " in the database.");
            return false;
        }
        db.close();
        return found;
    }

    private static void sortIntervals(List<Pair<Long, Range<Long>>> intervals) {
        Collections.sort(intervals, new Comparator<Pair<Long, Range<Long>>>() {
            @Override
            public int compare(Pair<Long, Range<Long>> lhs, Pair<Long, Range<Long>> rhs) {
                int intervalCompare = Long.compare(
                        lhs.getValue().getMinimum(), rhs.getValue().getMinimum());
                if (intervalCompare == 0) {
                    return Long.compare(lhs.getKey(), rhs.getKey());
                }
                return intervalCompare;
            }
        });
    }

    synchronized boolean updateInterval(long id, long newValue, boolean startTime) {
        mIntervals = getSortedIntervals();
        List<Pair<Long, Range<Long>>> updatedIntervals =
                new ArrayList<Pair<Long, Range<Long>>>(mIntervals.size());
        if (mIntervals.size() >= 2 &&
                mIntervals.get(0).getKey().equals(id) &&
                    mIntervals.get(mIntervals.size() - 1).getKey().equals(id)) {
            updatedIntervals.addAll(mIntervals.subList(1, mIntervals.size() - 1));
            long oldStart = mIntervals.get(0).getValue().getMaximum();
            long oldEnd = mIntervals.get(mIntervals.size() - 1).getValue().getMinimum();
            addIntervalToList(updatedIntervals, id, startTime ? newValue : oldStart,
                    startTime ? oldEnd : newValue);
        } else {
            boolean found = false;
            for (Pair<Long, Range<Long>> interval : mIntervals) {
                if (interval.getKey().equals(id)) {
                    if (found) {
                        Log.e(getClass().getCanonicalName(),
                                "Invalid split interval " + id + " in " + mIntervals.toString());
                    }
                    found = true;
                    Range<Long> newRange;
                    if (startTime) {
                        newRange = Range.between(newValue, interval.getValue().getMaximum());
                    } else {
                        newRange = Range.between(interval.getValue().getMinimum(), newValue);
                    }
                    // copy the changed interval
                    interval = Pair.of(interval.getKey(), newRange);
                }
                updatedIntervals.add(interval);
            }
            if (!found) {
                Log.e(getClass().getCanonicalName(),
                        "Could not find interval with id " + id + " in " + mIntervals.toString());
                return false;
            }
        }

        sortIntervals(updatedIntervals);
        if (areIntersecting(updatedIntervals)) {
            return false;
        }

        // Update interval in the DB
        ContentValues contentValues = new ContentValues();
        contentValues.put(startTime ? KEY_START : KEY_END, newValue);
        SQLiteDatabase db = getWritableDatabase();
        if (db.update(SCHEDULE_TABLE_NAME, contentValues,
                KEY_ID + "=?", new String[]{Long.toString(id)}) == 0) {
            Log.e(getClass().getCanonicalName(),
                    "Could not update interval with id " + id + " in the database.");
        }
        db.close();

        return true;
    }

    synchronized boolean addInterval(long id, long newValue, boolean startTime) {
        return true;
    }
}
