package delta.humanprofiler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.Calendar;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

/**
 * Created by Delta on 24/03/2016.
 */
public class DailySamplerDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "poll_times";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static DailySamplerDBHelper mInstance;
    private final int MINITE_IN_MILLIS = 60000;
    private final int FULL_DAY_IN_MILLIS = 24 * 60 * MINITE_IN_MILLIS;
    private Random mRandom = new Random();
    private Queue<Long> mData = null;
    private long mLastPoll = 0;
    private int mNumPollsPerDay = 1;

    private DailySamplerDBHelper(Context context) {
        super(context, context.getString(R.string.db_name) + "." + TABLE_NAME, null,
                DATABASE_VERSION);
    }

    public static synchronized DailySamplerDBHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DailySamplerDBHelper(context);
        }
        return mInstance;
    }

    private static long beginningOfTheDay(Calendar time) {
        Calendar copy = (Calendar) time.clone();
        copy.clear();
        copy.set(
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DAY_OF_MONTH));
        return copy.getTimeInMillis();
    }

    synchronized long size() {
        if (mData == null) {
            readFromDB();
        }
        return mData.size();
    }

    synchronized private void pop() {
        if (mData != null) {
            mLastPoll = mData.remove();
        }
    }

    synchronized long peek() {
        if (mData == null) {
            readFromDB();
        }

        Calendar now = Calendar.getInstance();
        while (!mData.isEmpty()) {
            long timestamp = mData.peek();
            if (timestamp >= now.getTimeInMillis() + MINITE_IN_MILLIS) {
                return timestamp;
            }
            pop();
        }

        // Generate more polls.
        long startTime = beginningOfTheDay(now);
        while (mLastPoll > startTime) {
            // Already polled today, schedule polling for tomorrow.
            startTime += FULL_DAY_IN_MILLIS;
        }
        generatePolls(startTime, FULL_DAY_IN_MILLIS, mNumPollsPerDay);

        return peek();
    }

    private synchronized void readFromDB() {
        if (mData == null) {
            mData = new PriorityQueue<Long>();
        }
        mData.clear();

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_TIMESTAMP},
                null, null, null, null, null);
        if (cursor.moveToFirst()) {
            do {
                mData.add(cursor.getLong(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
    }

    private synchronized void generatePolls(long beginning, int scope, int numPolls) {
        if (mData == null) {
            mData = new PriorityQueue<Long>(numPolls);
        }
        mData.clear();

        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);

        ContentValues row = new ContentValues();
        while (numPolls-- > 0) {
            long pollTime = beginning + mRandom.nextInt(scope);
            row.put(COLUMN_TIMESTAMP, pollTime);
            long rowId = db.insert(TABLE_NAME, null, row);
            if (rowId < 0) {
                Log.e(getClass().getCanonicalName(), "Failed to insert row " + row.toString());
            } else {
                Log.v(getClass().getCanonicalName(), "Added row " + rowId + ": " + row.toString());
                mData.add(pollTime);
            }
            row.clear();
        }
        db.close();
    }

    public synchronized int getNumPollsPerDay() {
        return mNumPollsPerDay;
    }

    public synchronized boolean setNumPollsPerDay(int value) {
        if (value > 0) {
            mNumPollsPerDay = value;
            while (size() > mNumPollsPerDay) {
                // Yeah, this biases the samples, but this corner case not very important.
                pop();
            }
            // Adding more poll times here would also be too complex for me to care.
            return true;
        }
        return false;
    }

    @Override
    public synchronized void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_TIMESTAMP + " INTEGER NOT NULL);");
    }

    @Override
    public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
