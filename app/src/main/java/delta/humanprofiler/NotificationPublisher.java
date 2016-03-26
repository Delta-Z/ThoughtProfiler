package delta.humanprofiler;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;

public class NotificationPublisher extends BroadcastReceiver {

    static final int NOTIFY = 1;
    static final int DELETE = 2;
    static final int DND = 3;
    static final int NOTIFICATION_TIMEOUT = 60;  // seconds
    public static String COMMAND = "delta.humanprofiler.NOTIFICATION_ACTION";
    static public Sampler sampler = new DailySampler();
    // Whether polling is currently active (or paused through configuration activity).
    static private boolean active = false;
    // Whether polling activity is currently active.
    static private boolean polling = false;

    static boolean isActive() {
        return active;
    }

    static private Notification newNotification(Context context) {
        Notification.Builder builder = new Notification.Builder(context);
        Intent pollIntent = new Intent(context, PollActivity.class);
        pollIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent notifyIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        pollIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        PendingIntent dismissedIntent =
                PendingIntent.getBroadcast(
                        context,
                        1,
                        newBroadcastIntent(context, DND),
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        builder.setContentIntent(notifyIntent)
                .setDeleteIntent(dismissedIntent)
                .setContentTitle("What were you thinking about?")
                .setContentText("Human profiler needs to know!")
                .setSmallIcon(R.drawable.ic_question)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.mipmap.ic_launcher))
                .setPriority(ConfigureActivity.getBooleanSetting(context,
                        ConfigureActivity.NOTIFICATION_PRIORITY_HIGH) ?
                        Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(false);
        return builder.build();
    }

    public static synchronized boolean switchSampler(Context context) {
        if (sampler instanceof MockSampler) {
            sampler = new DailySampler();
            return false;
        }
        sampler = new MockSampler();
        NotificationPublisher.scheduleNotification(context, true);
        return true;
    }

    public static synchronized void startFiringNotifications(Activity activity) {
        Context context = activity.getApplicationContext();
        if (activity instanceof ConfigureActivity) {
            active = true;
            context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context, NotificationPublisher.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } else if (activity instanceof PollActivity) {
            polling = false;
        } else {
            Log.e(NotificationPublisher.class.getCanonicalName(),
                    "unexpected startFiringNotifications from " +
                            activity.getClass().getCanonicalName());
        }
        NotificationPublisher.scheduleNotification(context, true);
    }

    private static Intent newBroadcastIntent(Context context, int command) {
        Intent intent = new Intent(context, NotificationPublisher.class);
        intent.setAction(COMMAND);
        intent.putExtra(NotificationPublisher.COMMAND, command);
        return intent;
    }

    static public synchronized void pauseNotifications(Activity activity, boolean justForPoll) {
        if (justForPoll) {
            polling = true;
        } else {
            active = false;
            Context context = activity.getApplicationContext();
            context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context, NotificationPublisher.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        if (!((activity instanceof PollActivity) || (activity instanceof ConfigureActivity))) {
            Log.e(NotificationPublisher.class.getCanonicalName(),
                    "unexpected pauseNotifications from " + activity.getClass().getCanonicalName());
        }
        NotificationManager notificationManager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFY);
    }

    private static boolean scheduleNotification(Context context, boolean wakeup) {
        if (polling || !active) {
            return false;
        }
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context, 0, newBroadcastIntent(context, NOTIFY),
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager =
                (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(wakeup ? AlarmManager.RTC_WAKEUP : AlarmManager.RTC,
                sampler.getNextSamplingTime(context), pendingIntent);
        return true;
    }

    private void scheduleNotificationExpiry(Context context, long delay) {
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context, 0, newBroadcastIntent(context, DELETE),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        //action, data, type, class, and categories

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, pendingIntent);
    }

    public void onReceive(Context context, Intent intent) {
        synchronized (NotificationPublisher.class) {
            if (polling || !active) {
                return;
            }
            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                scheduleNotification(context, true);
                return;
            }

            int command = intent.getIntExtra(COMMAND, 0);
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final SamplesDBHelper dbHelper = SamplesDBHelper.getInstance(context);
            switch (command) {
                case DND:
                    dbHelper.insertSample(context.getString(R.string.do_not_disturb_category));
                    Toast.makeText(context,
                            context.getString(R.string.stop_notifications_hint),
                            Toast.LENGTH_SHORT).show();
                    scheduleNotification(context, true);
                    break;
                case NOTIFY:
                    if (ScheduleDBHelper.getInstance(context).isCoveredByIntervals(
                            Calendar.getInstance())) {
                        // Not a valid time to show notification, skip and schedule the next one.
                        Log.i(getClass().getCanonicalName(),
                                "Skipped notification because of blackout schedule.");
                        scheduleNotification(context, true);
                        break;
                    }
                    notificationManager.notify(NOTIFY, newNotification(context));
                    scheduleNotificationExpiry(context, NOTIFICATION_TIMEOUT * 1000);
                    break;
                case DELETE:
                    notificationManager.cancel(NOTIFY);
                    if (ConfigureActivity.getBooleanSetting(context,
                            ConfigureActivity.MISSED_AS_DND)) {
                        dbHelper.insertSample(context.getString(R.string.do_not_disturb_category));
                    }
                    scheduleNotification(context, true);
                    break;
                default:
                    Log.e(getClass().getCanonicalName(), "wrong command id " + command);
            }
        }
    }
}
