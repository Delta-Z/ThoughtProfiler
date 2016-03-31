package delta.humanprofiler;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ConfigureActivity extends AppCompatActivity {
    public static final int MIN_SAMPLES_THRESHOLD = 5;
    public static final String POLLING_ENABLED = "POLLING_ENABLED";
    public static final String NOTIFICATION_PRIORITY_HIGH = "NOTIFICATION_PRIORITY_HIGH";
    public static final String MISSED_AS_DND = "MISSED_AS_DND";
    public static final String NUM_POLLS_PER_DAY = "NUM_POLLS_PER_DAY";

    private static final SimpleArrayMap<String, Boolean> BOOLEAN_DEFAULTS;

    static {
        SimpleArrayMap<String, Boolean> map = new SimpleArrayMap<String, Boolean>();
        map.put(POLLING_ENABLED, false);
        map.put(NOTIFICATION_PRIORITY_HIGH, false);
        map.put(MISSED_AS_DND, true);
        BOOLEAN_DEFAULTS = map;
    }

    static public SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE);
    }

    static boolean getBooleanSetting(Context context, String setting) {
        return getPreferences(context).getBoolean(setting, BOOLEAN_DEFAULTS.get(setting));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("Configure " + getString(R.string.app_name));
            actionBar.setIcon(R.mipmap.ic_launcher);
        }

        final Context context = getApplicationContext();
        if (NotificationPublisher.sampler instanceof DailySampler) {
            ((DailySampler) NotificationPublisher.sampler).setNumPollsPerDay(
                    context,
                    getPreferences(context).getInt(ConfigureActivity.NUM_POLLS_PER_DAY, 1));
        }

        ToggleButton pauseButton = (ToggleButton) findViewById(R.id.configure_polling_active_btn);
        pauseButton.setChecked(getBooleanSetting(getApplicationContext(), POLLING_ENABLED));
        pauseButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPreferences(context).edit().putBoolean(POLLING_ENABLED, isChecked).commit();
                if (isChecked) {
                    NotificationPublisher.resumeFiringNotifications(ConfigureActivity.this);
                    Toast.makeText(
                            context, getText(R.string.you_will_be_polled),
                            Toast.LENGTH_SHORT)
                            .show();
                } else {
                    NotificationPublisher.pauseNotifications(ConfigureActivity.this, false);
                }
            }
        });
        initBooleanSettingToggle(R.id.configure_notification_priority_btn,
                NOTIFICATION_PRIORITY_HIGH);
        initBooleanSettingToggle(R.id.configure_missed_as_dnd_btn,
                MISSED_AS_DND);
        SamplesDBHelper.getInstance(context).registerWatcher(new SamplesDBHelper.ChangeWatcher() {
            @Override
            public boolean onChange() {
                if (ConfigureActivity.this.isFinishing() || ConfigureActivity.this.isDestroyed()) {
                    return false;
                }
                ConfigureActivity.this.refreshUI();
                return true;
            }
        });

        if (BuildConfig.DEBUG) {
            Button schdButton = new Button(this);
            schdButton.setText("Cycle Schedulers");
            schdButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            NotificationPublisher.switchSampler(v.getContext());
                        }
                    });
            LinearLayout buttonsList = (LinearLayout) findViewById(R.id.configure_buttons_list);
            buttonsList.addView(schdButton);
        }

        refreshUI();
    }

    private void initBooleanSettingToggle(int buttonId, final String setting) {
        ToggleButton toggle = (ToggleButton) findViewById(buttonId);
        toggle.setChecked(getBooleanSetting(this, setting));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPreferences(getApplicationContext()).edit().putBoolean(setting, isChecked)
                        .commit();
            }
        });
    }

    private void refreshUI() {
        Context context = getApplicationContext();
        SamplesDBHelper dbHelper = SamplesDBHelper.getInstance(getApplicationContext());
        TextView numSamplesIndicator = (TextView) findViewById(R.id.numSamples);
        long numSamples = dbHelper.numSamples();
        numSamplesIndicator.setText(numSamples + " samples");
        Button changeLastButton = (Button) findViewById(R.id.change_last);
        changeLastButton.setEnabled(numSamples > 0);
        Button clearDataButton = (Button) findViewById(R.id.clear_data);
        clearDataButton.setEnabled(numSamples > 0);
        Button editCategoriesButton = (Button) findViewById(R.id.edit_categories);
        editCategoriesButton.setEnabled(numSamples > 0 &&
                !dbHelper.getCategories(context, true).isEmpty());
    }

    public void editCategories(View view) {
        startActivity(new Intent(this, EditCategories.class));
    }

    public void clearData(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Clear all data")
                .setMessage("Are you sure?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        SamplesDBHelper.getInstance(getApplicationContext()).clearData();
                        Toast.makeText(
                                getApplicationContext(), "Data cleared.", Toast.LENGTH_SHORT)
                                .show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    public void viewData(View view) {
        if (SamplesDBHelper.getInstance(getApplicationContext()).numSamples() <
                MIN_SAMPLES_THRESHOLD) {
            Toast.makeText(this, "You cannot view data before you have been sampled " +
                    MIN_SAMPLES_THRESHOLD + " times!", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, ViewDataActivity.class));
    }

    public void changeLast(View view) {
        NotificationPublisher.pauseNotifications(this, true);
        PollActivity.setChangeLast();
        startActivity(new Intent(this, PollActivity.class));
    }

    public void changeSchedule(View view) {
        startActivity(new Intent(this, ConfigureScheduleActivity.class));
    }
}
