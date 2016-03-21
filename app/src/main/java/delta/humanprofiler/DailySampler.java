package delta.humanprofiler;

import android.content.Context;
import android.util.Log;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

/**
 * Created by Delta on 20/03/2016.
 */
public class DailySampler implements Sampler {
    private final int FULL_DAY_IN_MINS = 24 * 60;
    private final long MINITE_IN_MILLIS = 60000;
    private int mNumPollsPerDay = 1;
    private Queue<Long> mNextPolls = new PriorityQueue<Long>(mNumPollsPerDay);
    private Calendar mLastPoll = null;

    @Override
    public synchronized long getNextSamplingTime(Context context) {
        Calendar now = Calendar.getInstance();
        if (mNextPolls.isEmpty()) {
            // Draw next day's polls.
            Calendar beginningOfTheDay = (Calendar) now.clone();
            beginningOfTheDay.clear(Calendar.MILLISECOND);
            beginningOfTheDay.clear(Calendar.SECOND);
            beginningOfTheDay.clear(Calendar.MINUTE);
            beginningOfTheDay.clear(Calendar.HOUR);
            beginningOfTheDay.clear(Calendar.HOUR_OF_DAY);
            Log.v(getClass().getCanonicalName(), "Beginning of the day: " +
                    DateFormat.getInstance().format(beginningOfTheDay.getTime()) +
                    " (" + beginningOfTheDay.getTimeZone().getDisplayName() + ").");
            if (mLastPoll != null && mLastPoll.after(beginningOfTheDay)) {
                // Already polled today, schedule polling for tomorrow.
                beginningOfTheDay.add(Calendar.DAY_OF_MONTH, 1);
            }
            Random random = new Random();
            do {
                Calendar randomPollTime = (Calendar) beginningOfTheDay.clone();
                randomPollTime.setLenient(true);
                randomPollTime.add(Calendar.MINUTE, random.nextInt(FULL_DAY_IN_MINS));
                Log.v(getClass().getCanonicalName(), "Drawn poll time: " +
                        DateFormat.getInstance().format(randomPollTime.getTime()));
                mNextPolls.add(randomPollTime.getTimeInMillis());
            } while (mNextPolls.size() < mNumPollsPerDay);
        }
        Long nextPoll = mNextPolls.peek();
        if (nextPoll > now.getTimeInMillis() + MINITE_IN_MILLIS) {
            return nextPoll;
        }
        if (mLastPoll == null) {
            mLastPoll = Calendar.getInstance();
        }
        mLastPoll.setTimeInMillis(mNextPolls.remove());
        return getNextSamplingTime(context);
    }

    public synchronized int getNumPollsPerDay() {
        return mNumPollsPerDay;
    }

    public synchronized boolean setNumPollsPerDay(int value) {
        if (value > 0) {
            mNumPollsPerDay = value;
            while (mNextPolls.size() > mNumPollsPerDay) {
                // Yeah, this biases the samples, but this corner case not very important.
                mNextPolls.remove();
            }
            // Adding more poll times here would also be too complex for me to care.
            return true;
        }
        return false;
    }
}
