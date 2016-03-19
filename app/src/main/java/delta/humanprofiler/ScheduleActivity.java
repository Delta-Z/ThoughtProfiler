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
import android.widget.TimePicker;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;

import org.apache.commons.lang3.tuple.Pair;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public class ScheduleActivity extends AppCompatActivity {

    private IntervalsAdapter mAdapter;

    private static long parseTimeString(String string) throws ParseException {
        Date date = DateFormat.getTimeInstance(DateFormat.SHORT).parse(string);
        return date.getTime();
    }

    private static String makeTimeString(long milliseconds) {
        Date date = new Date();
        date.setTime(milliseconds);
        // TODO: inconsistent with using Calendar elsewhere.
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(date);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.polling_disabled_activity_title));
            actionBar.setIcon(R.mipmap.ic_launcher);
        }
        mAdapter = new IntervalsAdapter(this);
        ListView listView = (ListView) findViewById(R.id.schedule_list_view);
        listView.setAdapter(mAdapter);
        // Add interval handler?
        // *********************
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.resetCursor();
    }

    public void addInterval(View view) {
        ScheduleTimePicker picker = new ScheduleTimePicker(mAdapter, true, 0, true, 0, null);
        picker.run();
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
                ScheduleDBHelper dbHelper = ScheduleDBHelper.getInstance(ScheduleActivity.this);
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
            Pair<Integer, Integer> hourAndMinute =
                    ScheduleDBHelper.hourAndMinuteFromTimestamp(mAskForStart ? mStart : mEnd);
            TimePickerDialog timePickerDialog = new TimePickerDialog(ScheduleActivity.this, this,
                    hourAndMinute.getLeft(), hourAndMinute.getRight(),
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
            fromBtn.setText(makeTimeString(startTime));
            fromBtn.setOnClickListener(updateBtnListener);
            toBtn.setText(makeTimeString(endTime));
            toBtn.setOnClickListener(updateBtnListener);
            removeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ScheduleDBHelper.getInstance(ScheduleActivity.this).deleteInterval(id)) {
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
