package delta.humanprofiler;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class NotificationPublisher extends BroadcastReceiver {

    static final int SCHEDULE = 1;
    static final int NOTIFY = 2;
    static final int DELETE = 3;
    static final int DND = 5;
    static final int NOTIFICATION_TIMEOUT = 60;  // seconds
    public static String COMMAND = "delta.humanprofiler.cmd";
    // Whether polling is currently active (or paused through configuration activity).
    static private boolean active = false;
    // Whether polling activity is currently active.
    static private boolean polling = false;

    static private Sampler sampler = new MockSampler();

    static boolean isActive() {
        return active;
    }

    static private Notification newNotification(Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
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
                .setPriority(ConfigureActivity.getBooleanSetting(context, ConfigureActivity.NOTIFICATION_PRIORITY_HIGH) ? Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(false);
        return builder.build();
    }

    public static synchronized void scheduleNotification(Activity activity) {
        if (activity instanceof ConfigureActivity) {
            active = true;
        } else if (activity instanceof PollActivity) {
            polling = false;
        } else {
            Log.e(NotificationPublisher.class.getCanonicalName(),
                    "unexpected scheduleNotification from " + activity.getClass().getCanonicalName());
        }
        activity.sendBroadcast(newBroadcastIntent(activity, SCHEDULE));
    }

    private static Intent newBroadcastIntent(Context context, int command) {
        Intent intent = new Intent(context, NotificationPublisher.class);
        intent.putExtra(NotificationPublisher.COMMAND, command);
        return intent;
    }

    static public synchronized void pauseNotifications(Activity activity, boolean justForPoll) {
        if (justForPoll) {
            polling = true;
        } else {
            active = false;
        }
        if (!((activity instanceof PollActivity) || (activity instanceof ConfigureActivity))) {
            Log.e(NotificationPublisher.class.getCanonicalName(),
                    "unexpected pauseNotifications from " + activity.getClass().getCanonicalName());
        }
        NotificationManager notificationManager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFY);
    }

    private void scheduleIntent(Context context, Intent intent, long timestamp, boolean wakeup) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager =
                (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(wakeup ? AlarmManager.ELAPSED_REALTIME_WAKEUP : AlarmManager.ELAPSED_REALTIME,
                timestamp, pendingIntent);
    }

    public void onReceive(Context context, Intent intent) {
        synchronized (NotificationPublisher.class) {
            if (polling || !active) {
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
                case SCHEDULE:
                    scheduleIntent(context, newBroadcastIntent(context, NOTIFY),
                            sampler.getNextSamplingTime(), true);
                    break;
                case NOTIFY:
                    notificationManager.notify(NOTIFY, newNotification(context));
                    long timestamp = SystemClock.elapsedRealtime() + NOTIFICATION_TIMEOUT * 1000;
                    scheduleIntent(context, newBroadcastIntent(context, DELETE), timestamp, true);
                    break;
                case DELETE:
                    notificationManager.cancel(NOTIFY);
                    if (ConfigureActivity.getBooleanSetting(context, ConfigureActivity.MISSED_AS_DND)) {
                        dbHelper.insertSample(context.getString(R.string.do_not_disturb_category));
                    }
                    context.sendBroadcast(newBroadcastIntent(context, SCHEDULE));
                    break;
                default:
                    Log.e(getClass().getCanonicalName(), "wrong command id " + command);
            }
        }
    }
}
