package delta.humanprofiler;

import android.content.Context;

/**
 * Created by Delta on 08/12/2015.
 */
public interface Sampler {
    long getNextSamplingTime(Context context);
}
