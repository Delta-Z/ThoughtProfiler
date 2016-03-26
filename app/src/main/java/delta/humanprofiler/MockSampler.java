package delta.humanprofiler;

import android.content.Context;

import java.util.Calendar;

/**
 * Created by Delta on 08/12/2015.
 */
public class MockSampler implements Sampler {
    @Override
    public long getNextSamplingTime(Context unused_context) {
        return Calendar.getInstance().getTimeInMillis() + 10000;
    }
}
