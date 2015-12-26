package delta.humanprofiler;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class EditCategories extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_categories);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Edit Categories");
    }

    private class EditCategoryListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.add_new_category_button) {
                Log.e(this.getClass().getCanonicalName(),
                        "new category button in EditCategoryListener");
                return;
            }
            Button button = (Button) v;
            CharSequence category = button.getText();
            Utils.CreateRenameDialog(EditCategories.this, category).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCategories();
    }

    void refreshCategories() {
        Utils.CreateCategoryButtons(this, R.id.category_list, new EditCategoryListener(), false);
    }
}
