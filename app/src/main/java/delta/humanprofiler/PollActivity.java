package delta.humanprofiler;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class PollActivity extends AppCompatActivity {
    private static boolean changeLast = false;

    static synchronized void setChangeLast() {
        changeLast = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationPublisher.pauseNotifications(this, true);

        setContentView(R.layout.activity_poll);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("What were you thinking about?");
            actionBar.setIcon(R.mipmap.ic_launcher);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Utils.CreateCategoryButtons(this, R.id.responses, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.add_new_category_button) {
                    Utils.CreateNewCategoryDialog(PollActivity.this).show();
                } else {
                    answer(((Button) v).getText().toString());
                }
            }
        }, true);
    }

    void answer(String category) {
        synchronized (PollActivity.class) {
            if (changeLast) {
                SamplesDBHelper.getInstance(getApplicationContext()).changeLast(category);
                changeLast = false;
            } else {
                SamplesDBHelper.getInstance(getApplicationContext()).insertSample(category);
            }
        }
        finish();
    }

    public void doNotDisturb(View v) {
        answer(getString(R.string.do_not_disturb_category));
    }

    @Override
    public void onStop() {
        super.onStop();
        NotificationPublisher.scheduleNotification(this);
    }
}
