package delta.humanprofiler;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;

import org.apache.commons.lang3.tuple.Pair;

import java.text.DateFormat;
import java.util.Date;

public class ConfigureScheduleActivity extends AppCompatActivity {

    private IntervalsAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_schedule);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.configure_schedule_and_frequency));
            actionBar.setIcon(R.mipmap.ic_launcher);
        }
        mAdapter = new IntervalsAdapter(this);
        ListView listView = (ListView) findViewById(R.id.schedule_list_view);
        listView.setAdapter(mAdapter);
        SeekBar pollingFrequency = (SeekBar) findViewById(R.id.maxPollsPerDayBar);
        pollingFrequency.setOnSeekBarChangeListener(
                new FrequencySeekerListener((TextView) findViewById(R.id.maxPollsPerDayText),
                        getApplicationContext()));
        // Add interval handler?
        // *********************
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.resetCursor();
        Context context = getApplicationContext();

        SeekBar pollingFrequency = (SeekBar) findViewById(R.id.maxPollsPerDayBar);
        pollingFrequency.setProgress(
                ((DailySampler) NotificationPublisher.sampler).getNumPollsPerDay(context) - 1);

        if (BuildConfig.DEBUG) {
            Toast.makeText(
                    context,
                    DateFormat.getInstance().format(
                            new Date(NotificationPublisher.sampler.getNextSamplingTime(context))),
                    Toast.LENGTH_LONG).show();
        }
    }

    public void addInterval(View view) {
        ScheduleTimePicker picker = new ScheduleTimePicker(mAdapter, true, -1, true, -1, null);
        picker.run();
    }

    private class FrequencySeekerListener implements SeekBar.OnSeekBarChangeListener {

        private TextView mInfoText;
        private DailySampler mSampler;
        private Context mContext;

        public FrequencySeekerListener(TextView infoText, Context context) {
            mInfoText = infoText;
            mSampler = (DailySampler) NotificationPublisher.sampler;
            mContext = context;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int numPolls = progress + 1;
            mInfoText.setText(String.format("at most %d per day", numPolls));
            Context context = getApplicationContext();
            if (numPolls != mSampler.getNumPollsPerDay(context)) {
                mSampler.setNumPollsPerDay(context, numPolls);
                ConfigureActivity.getPreferences(mContext).edit().putInt(
                        ConfigureActivity.NUM_POLLS_PER_DAY, numPolls).commit();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private class ScheduleTimePicker implements TimePickerDialog.OnTimeSetListener {
        private final Long mIntervalToDelete;
        private final IntervalsAdapter mAdapter;
        private boolean mAskForStart, mAskForEnd;
        private long mStart, mEnd;

        public ScheduleTimePicker(IntervalsAdapter adapter, boolean askForStart, long start,
                                  boolean askForEnd, long end, Long intervalToDelete) {
            mAdapter = adapter;
            mAskForStart = askForStart;
            mAskForEnd = askForEnd;
            mStart = start;
            mEnd = end;
            mIntervalToDelete = intervalToDelete;
        }

        public void run() {
            if (!ShowTimePickerIfNecessary()) {
                if (mStart == mEnd) {
                    return;
                }
                ScheduleDBHelper dbHelper =
                        ScheduleDBHelper.getInstance(ConfigureScheduleActivity.this);
                if (mIntervalToDelete != null && !dbHelper.deleteInterval(mIntervalToDelete)) {
                    return;
                }
                if (mStart < mEnd) {
                    dbHelper.addIntervals(ImmutableRangeSet.of(Range.closedOpen(mStart, mEnd)));
                } else {
                    ImmutableRangeSet.Builder<Long> intervalBuilder =
                            new ImmutableRangeSet.Builder<Long>()
                                    .add(Range.closedOpen(
                                            mStart,
                                            ScheduleDBHelper.timestampFromHourAndMinute(24, 0)))
                                    .add(Range.closedOpen(
                                            ScheduleDBHelper.timestampFromHourAndMinute(0, 0),
                                            mEnd));
                    dbHelper.addIntervals(intervalBuilder.build());
                }
                mAdapter.resetCursor();
            }
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            if (!view.isShown()) {
                // A known "bug": onTimeSet is unnecessarily called when the dialog is dismissed.
                return;
            }
            if (mAskForStart) {
                mStart = ScheduleDBHelper.timestampFromHourAndMinute(hourOfDay, minute);
                mAskForStart = false;
            } else if (mAskForEnd) {
                mEnd = ScheduleDBHelper.timestampFromHourAndMinute(hourOfDay, minute);
                mAskForEnd = false;
            }
            run();
        }

        private boolean ShowTimePickerIfNecessary() {
            if (!(mAskForEnd || mAskForStart)) {
                return false;
            }
            long defaultValue = mAskForStart ? mStart : mEnd;
            if (defaultValue < 0) {
                defaultValue = Math.max(0, Math.max(mStart, mEnd));
            }
            Pair<Integer, Integer> hourAndMinute =
                    ScheduleDBHelper.hourAndMinuteFromTimestamp(defaultValue);
            TimePickerDialog timePickerDialog = new TimePickerDialog(ConfigureScheduleActivity.this,
                    this, hourAndMinute.getLeft(), hourAndMinute.getRight(),
                    android.text.format.DateFormat.is24HourFormat(getApplicationContext()));
            timePickerDialog.setTitle(mAskForStart ? "Start Time" : "End Time");
            timePickerDialog.show();
            return true;
        }
    }

    private class IntervalsAdapter extends ResourceCursorAdapter {
        private final Context mContext;

        IntervalsAdapter(Activity activity) {
            super(activity, R.layout.schedule_blackout,
                    ScheduleDBHelper.getInstance(activity).getIntervalsCursor().cursor(), 0);
            mContext = activity.getApplicationContext();
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Button fromBtn = (Button) view.findViewById(R.id.schedule_blackout_from_btn);
            Button toBtn = (Button) view.findViewById(R.id.schedule_blackout_to_btn);
            Button removeBtn = (Button) view.findViewById(R.id.schedule_blackout_remove_btn);
            ScheduleDBHelper.IntervalsCursor intervals =
                    new ScheduleDBHelper.IntervalsCursor(cursor);
            if (intervals.cursor().getColumnCount() < 3) {
                Log.e(getClass().getCanonicalName(),
                        "Unexpected number of columns: " + intervals.cursor().getColumnCount());
                fromBtn.setEnabled(false);
                toBtn.setEnabled(false);
                removeBtn.setEnabled(false);
                return;
            }
            final long id = intervals.id();
            final long startTime = intervals.range().lowerEndpoint();
            final long endTime = intervals.range().upperEndpoint();
            View.OnClickListener updateBtnListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ScheduleTimePicker picker = new ScheduleTimePicker(
                            mAdapter,
                            v.getId() == R.id.schedule_blackout_from_btn, startTime,
                            v.getId() == R.id.schedule_blackout_to_btn, endTime, id);
                    picker.run();
                }
            };
            fromBtn.setText(ScheduleDBHelper.makeTimeString(context, startTime));
            fromBtn.setOnClickListener(updateBtnListener);
            toBtn.setText(ScheduleDBHelper.makeTimeString(context, endTime));
            toBtn.setOnClickListener(updateBtnListener);
            removeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ScheduleDBHelper.getInstance(ConfigureScheduleActivity.this)
                            .deleteInterval(id)) {
                        resetCursor();
                    }
                }
            });
        }

        public void resetCursor() {
            changeCursor(ScheduleDBHelper.getInstance(mContext).getIntervalsCursor().cursor());
            notifyDataSetChanged();
        }
    }
}
