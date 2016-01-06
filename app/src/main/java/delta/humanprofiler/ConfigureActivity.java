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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ConfigureActivity extends AppCompatActivity {
    public static final int MIN_SAMPLES_THRESHOLD = 5;
    public static final String POLLING_ENABLED = "POLLING_ENABLED";
    public static final String NOTIFICATION_PRIORITY_HIGH = "NOTIFICATION_PRIORITY_HIGH";
    public static final String MISSED_AS_DND = "MISSED_AS_DND";

    private static final SimpleArrayMap<String, Boolean> BOOLEAN_DEFAULTS;

    static {
        SimpleArrayMap<String, Boolean> map = new SimpleArrayMap<String, Boolean>();
        map.put(POLLING_ENABLED, true);
        map.put(NOTIFICATION_PRIORITY_HIGH, false);
        map.put(MISSED_AS_DND, true);
        BOOLEAN_DEFAULTS = map;
    }

    static private SharedPreferences getPreferences(Context context) {
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
        boolean polling_enabled = getBooleanSetting(getApplicationContext(), POLLING_ENABLED);
        if (polling_enabled ^ NotificationPublisher.isActive()) {
            if (polling_enabled) {
                NotificationPublisher.scheduleNotification(ConfigureActivity.this);
            } else {
                NotificationPublisher.pauseNotifications(ConfigureActivity.this, false);
            }
        }
        ToggleButton pauseButton = (ToggleButton) findViewById(R.id.configure_polling_active_btn);
        pauseButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    NotificationPublisher.scheduleNotification(ConfigureActivity.this);
                    Toast.makeText(
                            getApplicationContext(), getText(R.string.you_will_be_polled),
                            Toast.LENGTH_SHORT)
                            .show();
                } else {
                    NotificationPublisher.pauseNotifications(ConfigureActivity.this, false);
                }
                getPreferences(getApplicationContext()).edit().putBoolean(POLLING_ENABLED, NotificationPublisher.isActive()).commit();
                refreshPauseButton();
            }
        });
        initBooleanSettingToggle(R.id.configure_notification_priority_btn,
                NOTIFICATION_PRIORITY_HIGH);
        initBooleanSettingToggle(R.id.configure_missed_as_dnd_btn,
                MISSED_AS_DND);
        SamplesDBHelper.getInstance(getApplicationContext()).registerWatcher(new SamplesDBHelper.ChangeWatcher() {
            @Override
            public boolean onChange() {
                if (ConfigureActivity.this.isFinishing() || ConfigureActivity.this.isDestroyed()) {
                    return false;
                }
                ConfigureActivity.this.refreshNumSamples();
                return true;
            }
        });
        refreshNumSamples();
    }

    private void initBooleanSettingToggle(int buttonId, final String setting) {
        ToggleButton toggle = (ToggleButton) findViewById(buttonId);
        toggle.setChecked(getBooleanSetting(this, setting));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPreferences(getApplicationContext()).edit().putBoolean(setting, isChecked).commit();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPauseButton();
    }

    private void refreshNumSamples() {
        TextView numSamplesIndicator = (TextView) findViewById(R.id.numSamples);
        long numSamples = SamplesDBHelper.getInstance(getApplicationContext()).numSamples();
        numSamplesIndicator.setText(numSamples + " samples");
        Button changeLastButton = (Button) findViewById(R.id.change_last);
        changeLastButton.setEnabled(numSamples > 0);
    }

    private void refreshPauseButton() {
        ToggleButton pauseButton = (ToggleButton) findViewById(R.id.configure_polling_active_btn);
        pauseButton.setChecked(NotificationPublisher.isActive());
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
        if (SamplesDBHelper.getInstance(getApplicationContext()).numSamples() < MIN_SAMPLES_THRESHOLD) {
            Toast.makeText(this, "You cannot view data before you have been sampled " + MIN_SAMPLES_THRESHOLD + " times!", Toast.LENGTH_LONG).show();
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
        startActivity(new Intent(this, ScheduleActivity.class));
    }
}
