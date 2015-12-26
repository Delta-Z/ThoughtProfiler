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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Polling Disabled Times");
        actionBar.setIcon(R.drawable.ic_notification);
        ListView listView = (ListView) findViewById(R.id.schedule_list_view);
        listView.setAdapter(new IntervalsAdapter(this));
        // Add interval handler
        // ********************
    }

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

    private class IntervalsAdapter extends ResourceCursorAdapter {
        private final Activity mActivity;

        IntervalsAdapter(Activity activity) {
            super(activity, R.layout.schedule_blackout,
                    ScheduleDBHelper.getInstance(activity).getIntervalsCursor().cursor(),
                    FLAG_REGISTER_CONTENT_OBSERVER);
            mActivity = activity;
        }

        private class UpdateClickListener implements View.OnClickListener {
            private final Activity mActivity;
            private final long mId;
            private final long mInitialValue;
            private final boolean mStartTime;

            UpdateClickListener(Activity activity, long id, long initialValue, boolean startTime) {
                mActivity = activity;
                mId = id;
                mInitialValue = initialValue;
                mStartTime = startTime;
            }

            @Override
            public void onClick(View v) {
                Pair<Integer, Integer> hourAndMinute =
                        ScheduleDBHelper.hourAndMinuteFromTime(mInitialValue);
                TimePickerDialog timePickerDialog = new TimePickerDialog(mActivity,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                if (ScheduleDBHelper.getInstance(mActivity).updateInterval(
                                        mId,
                                        ScheduleDBHelper.timeFromHourAndMinute(hourOfDay, minute),
                                        mStartTime)) {
                                    IntervalsAdapter.this.notifyDataSetInvalidated();
                                }
                            }
                }, hourAndMinute.getLeft(), hourAndMinute.getRight(), true);
                timePickerDialog.show();
            }
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
            fromBtn.setOnClickListener(new UpdateClickListener(mActivity, id, startTime, true));
            toBtn.setText(makeTimeString(endTime));
            toBtn.setOnClickListener(new UpdateClickListener(mActivity, id, endTime, false));
            removeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ScheduleDBHelper.getInstance(mActivity).deleteInterval(id)) {
                        IntervalsAdapter.this.notifyDataSetChanged();
                    }
                }
            });
        }
    }
}
