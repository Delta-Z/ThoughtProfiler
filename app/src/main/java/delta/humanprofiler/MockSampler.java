package delta.humanprofiler;

import android.content.Context;
import android.os.SystemClock;

/**
 * Created by Delta on 08/12/2015.
 */
public class MockSampler implements Sampler {
    @Override
    public long getNextSamplingTime(Context unused_context) {
        return SystemClock.elapsedRealtime() + 5000;
    }
}
