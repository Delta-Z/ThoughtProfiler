package delta.humanprofiler;

import android.content.Context;

/**
 * Created by Delta on 20/03/2016.
 */
public class DailySampler implements Sampler {
    @Override
    public synchronized long getNextSamplingTime(Context context) {
        return DailySamplerDBHelper.getInstance(context).peek();
    }

    public int getNumPollsPerDay(Context context) {
        return DailySamplerDBHelper.getInstance(context).getNumPollsPerDay();
    }

    public boolean setNumPollsPerDay(Context context, int value) {
        return DailySamplerDBHelper.getInstance(context).setNumPollsPerDay(value);
    }
}
