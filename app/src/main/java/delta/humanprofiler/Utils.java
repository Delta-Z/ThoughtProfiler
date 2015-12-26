package delta.humanprofiler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Utils {

    static public boolean IsValidUserCategory(Context context, String category) {
        return category.length() > 0 &&
                !category.equalsIgnoreCase(context.getString(R.string.do_not_disturb_category));
    }

    static public ViewGroup CreateCategoryButtons(Activity activity, int containerId,
            View.OnClickListener listener, boolean allowAddNew) {
        ViewGroup container = (ViewGroup) activity.findViewById(containerId);
        container.removeAllViews();
        for (String category :
                SamplesDBHelper.getInstance(activity.getApplicationContext()).getCategories()) {
            if (!IsValidUserCategory(activity, category)) {
                continue;
            }
            Button button = new Button(activity);
            button.setText(category);
            button.setOnClickListener(listener);
            container.addView(button);
        }
        if (allowAddNew) {
            Button addNew = new Button(activity);
            addNew.setText(R.string.add_category_button);
            addNew.setId(R.id.add_new_category_button);
            addNew.setOnClickListener(listener);
            container.addView(addNew);
        }
        return container;
    }

    private static abstract class DialogClickListener implements DialogInterface.OnClickListener {
        private final EditText editText;
        private final Activity activity;

        public DialogClickListener(Activity activity, EditText editText) {
            this.activity = activity;
            this.editText = editText;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String category = editText.getText().toString();
            if (IsValidUserCategory(activity, category)) {
                performAction(category);
            } else {
                Toast.makeText(activity, "Invalid category name.", Toast.LENGTH_LONG).show();
            }
        }

        public abstract void performAction(String category);
    }

    static private AlertDialog CreateDialog(final Activity activity, final String title,
                                            EditText editText,
                                            final DialogClickListener okListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        editText.setSingleLine();
        builder.setView(editText);
        builder.setTitle(title)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    static public AlertDialog CreateRenameDialog(
            final EditCategories activity,
            final CharSequence category) {
        EditText editText = new EditText(activity);
        editText.setText(category);
        return CreateDialog(activity, "Rename " + category, editText,
                new DialogClickListener(activity, editText) {
                    public void performAction(String new_category) {
                        if (new_category == category) {
                            return;
                        }
                        SamplesDBHelper.getInstance(activity.getApplicationContext())
                                .renameCategory(category, new_category);
                        activity.refreshCategories();
                    }
                });
    }

    static public AlertDialog CreateNewCategoryDialog(final PollActivity activity) {
        final EditText editText = new EditText(activity);
        return CreateDialog(activity, activity.getString(R.string.new_category), editText,
                new DialogClickListener(activity, editText) {
                    public void performAction(String new_category) {
                        activity.answer(new_category);
                    }
                });
    }
}
