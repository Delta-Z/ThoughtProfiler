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
        // Add interval handler
        // ********************
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.resetCursor();
    }

    public void addInterval(View v) {
        long id = ScheduleDBHelper.getInstance(this).addInterval();
        new ScheduleTimePickerDialog(mAdapter, id, 0, true).show();
        new ScheduleTimePickerDialog(mAdapter, id, 0, false).show();
        mAdapter.resetCursor();
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
            final long startTime = intervals.startTime();
            final long endTime = intervals.endTime();
            fromBtn.setText(makeTimeString(startTime));
            fromBtn.setOnClickListener(
                    new ScheduleTimePickerDialog(this, id, startTime, true));
            toBtn.setText(makeTimeString(endTime));
            toBtn.setOnClickListener(
                    new ScheduleTimePickerDialog(this, id, endTime, false));
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

    private class ScheduleTimePickerDialog implements View.OnClickListener {
        private final long mId;
        private final long mInitialValue;
        private final boolean mStartTime;
        private final IntervalsAdapter mAdapter;

        ScheduleTimePickerDialog(
                IntervalsAdapter adapter, long id, long initialValue, boolean startTime) {
            mId = id;
            mInitialValue = initialValue;
            mStartTime = startTime;
            mAdapter = adapter;
        }

        public void show() {
            Pair<Integer, Integer> hourAndMinute =
                    ScheduleDBHelper.hourAndMinuteFromTime(mInitialValue);
            TimePickerDialog timePickerDialog = new TimePickerDialog(ScheduleActivity.this,
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            if (ScheduleDBHelper.getInstance(ScheduleActivity.this).updateInterval(
                                    mId,
                                    ScheduleDBHelper.timeFromHourAndMinute(hourOfDay, minute),
                                    mStartTime)) {
                                mAdapter.resetCursor();
                            }
                        }
                    }, hourAndMinute.getLeft(), hourAndMinute.getRight(), true);
            timePickerDialog.show();
        }

        @Override
        public void onClick(View v) {
            show();
        }
    }
}
