package delta.humanprofiler;

import android.os.SystemClock;

/**
 * Created by Delta on 08/12/2015.
 */
public class MockSampler implements Sampler {
    @Override
    public long getNextSamplingTime() {
        return SystemClock.elapsedRealtime() + 5000;
    }
}
