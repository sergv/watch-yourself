/**
 * TimeTracker+
 * ©2008, 2009 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.NAME;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_COLUMNS;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.DBHelper.TASK_TABLE;
import static net.ser1.timetracker.Report.weekEnd;
import static net.ser1.timetracker.Report.weekStart;
import static net.ser1.timetracker.TimeRange.NULL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.method.SingleLineTransformationMethod;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.DateFormat;

/**
 * Manages and displays a list of tasks, providing the ability to edit and
 * display individual task items.
 * @author ser
 */
public class Tasks extends ListActivity {
    public static final String TIMETRACKERPREF = "timetracker.pref";
    protected static final String FONTSIZE = "font-size";
    protected static final String MILITARY = "military-time";
    protected static final String CONCURRENT = "concurrent-tasks";
    protected static final String SOUND = "sound-enabled";
    protected static final String VIBRATE = "vibrate-enabled";

    protected static final String START_DAY = "start_day";
    protected static final String START_DATE = "start_date";
    protected static final String END_DATE = "end_date";
    protected static final String VIEW_MODE = "view_mode";
    protected static final String REPORT_DATE = "report_date";
    /**
     * Defines how each task's time is displayed 
     */
    private static final String FORMAT = "%02d:%02d";
    /**
     * How often to refresh the display, in milliseconds
     */
    private static final int REFRESH_MS = 60000;
    /**
     * The model for this view
     */
    private TaskAdapter adapter;
    /**
     * A timer for refreshing the display.
     */
    private Handler timer;
    /**
     * The call-back that actually updates the display.
     */
    private TimerTask updater;
    /**
     * The currently active task (the one that is currently being timed).  There
     * can be only one.
     */
    private boolean running = false;
    /**
     * The currently selected task when the context menu is invoked.
     */
    private Task selectedTask;
    private SharedPreferences preferences;
    private static int fontSize = 16;
    private boolean concurrency;
    private static MediaPlayer clickPlayer;
    private boolean playClick = false;
    private boolean vibrateClick = true;
    private Vibrator vibrateAgent;
    /**
     * A list of menu options, including both context and options menu items 
     */
    protected static final int ADD_TASK = 0,
            EDIT_TASK = 1,  DELETE_TASK = 2,  REPORT = 3,  SHOW_TIMES = 4,
            CHANGE_VIEW = 5,  SELECT_START_DATE = 6,  SELECT_END_DATE = 7,
            HELP = 8,  EXPORT_VIEW = 9,  SUCCESS_DIALOG = 10,  ERROR_DIALOG = 11,
            SET_WEEK_START_DAY = 12,  MORE = 13,  BACKUP = 14, PREFERENCES = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //android.os.Debug.waitForDebugger();
        preferences = getSharedPreferences( TIMETRACKERPREF,MODE_PRIVATE);
        fontSize = preferences.getInt(FONTSIZE, 16);
        concurrency = preferences.getBoolean(CONCURRENT, false);
        if (preferences.getBoolean(MILITARY, true)) {
            TimeRange.FORMAT = new SimpleDateFormat("HH:mm");
        } else {
            TimeRange.FORMAT = new SimpleDateFormat("hh:mm a");
        }

        int which = preferences.getInt(VIEW_MODE, 0);
        if (adapter == null) {
            adapter = new TaskAdapter(this);
            setListAdapter(adapter);
            switchView(which);
        }
        if (timer == null) {
            timer = new Handler();
        }
        if (updater == null) {
            updater = new TimerTask() {

                @Override
                public void run() {
                    if (running) {
                        adapter.notifyDataSetChanged();
                        setTitle();
                        Tasks.this.getListView().invalidate();
                    }
                    timer.postDelayed(this, REFRESH_MS);
                }
            };
        }
        playClick = preferences.getBoolean(SOUND, false);
        if (playClick && clickPlayer == null) {
            clickPlayer = MediaPlayer.create(this, R.raw.click);
            try {
                clickPlayer.prepareAsync();
            } catch (IllegalStateException illegalStateException) {
                // ignore this.  There's nothing the user can do about it.
                Logger.getLogger("TimeTracker").log(Level.SEVERE,
                        "Failed to set up audio player: "
                        +illegalStateException.getMessage());
            }
        }
        registerForContextMenu(getListView());
        if (adapter.tasks.size() == 0) {
            showDialog(HELP);
        }
        vibrateAgent = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        vibrateClick = preferences.getBoolean(VIBRATE, true);
    }

    @Override
    protected void onPause() {
        if (timer != null) {
            timer.removeCallbacks(updater);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (adapter != null)
            adapter.close();
        if (clickPlayer != null)
            clickPlayer.release();
        super.onStop();
    }

    @Override
    protected void onResume() {
        // This is only to cause the view to reload, so that we catch 
        // updates to the time list.
        int which = preferences.getInt(VIEW_MODE, 0);
        switchView(which);

        if (timer != null && running) {
            timer.post(updater);
        }
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, ADD_TASK, 0, R.string.add_task_title).setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, REPORT, 1, R.string.generate_report_title).setIcon(android.R.drawable.ic_menu_week);
        menu.add(0, MORE, 2, R.string.more).setIcon(android.R.drawable.ic_menu_more);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        menu.setHeaderTitle("Task menu");
        menu.add(0, EDIT_TASK, 0, "Edit Task");
        menu.add(0, DELETE_TASK, 0, "Delete Task");
        menu.add(0, SHOW_TIMES, 0, "Show times");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        selectedTask = (Task) adapter.getItem((int) info.id);
        switch (item.getItemId()) {
            case SHOW_TIMES:
                Intent intent = new Intent(this, TaskTimes.class);
                intent.putExtra(TASK_ID, selectedTask.getId());
                if (adapter.currentRangeStart != -1) {
                    intent.putExtra(START, adapter.currentRangeStart);
                    intent.putExtra(END, adapter.currentRangeEnd);
                }
                startActivity(intent);
                break;
            default:
                showDialog(item.getItemId());
                break;
        }
        return super.onContextItemSelected(item);
    }
    private AlertDialog exportSucceed;
    private String exportMessage;
    private String baseTitle;

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case ADD_TASK:
            case MORE:
                showDialog(item.getItemId());
                break;
            case REPORT:
                Intent intent = new Intent(this, Report.class);
                intent.putExtra(REPORT_DATE, System.currentTimeMillis());
                intent.putExtra(START_DAY, preferences.getInt(START_DAY, 0) + 1);
                startActivity(intent);
                break;
            default:
                // Ignore the other menu items; they're context menu
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case ADD_TASK:
                return openNewTaskDialog();
            case EDIT_TASK:
                return openEditTaskDialog();
            case DELETE_TASK:
                return openDeleteTaskDialog();
            case CHANGE_VIEW:
                return openChangeViewDialog();
            case HELP:
                return openAboutDialog();
            case SUCCESS_DIALOG:
                exportSucceed = new AlertDialog.Builder(Tasks.this)
                    .setTitle(R.string.success)
                    .setIcon(android.R.drawable.stat_notify_sdcard)
                    .setMessage(exportMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
                return exportSucceed;
            case ERROR_DIALOG:
                return new AlertDialog.Builder(Tasks.this).setTitle(R.string.failure).setIcon(android.R.drawable.stat_notify_sdcard).setMessage(exportMessage).setPositiveButton(android.R.string.ok, null).create();
            case MORE:
                return new AlertDialog.Builder(Tasks.this).setItems(R.array.moreMenu, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: // CHANGE_VIEW:
                                showDialog(CHANGE_VIEW);
                                break;
                            case 1: // EXPORT_VIEW:
                                String fname = export();
                                if (fname != null) {
                                    exportMessage = getString(R.string.export_csv_success, fname);
                                    if (exportSucceed != null) {
                                        exportSucceed.setMessage(exportMessage);
                                    }
                                    showDialog(SUCCESS_DIALOG);
                                } else {
                                    exportMessage = getString(R.string.export_csv_fail);
                                    showDialog(ERROR_DIALOG);
                                }
                                break;
                            case 2: // COPY DB TO SD
                                try {
                                    copyDbToSd();
                                    exportMessage = getString(R.string.backup_success);
                                    showDialog(SUCCESS_DIALOG);
                                } catch (Exception ex) {
                                    Logger.getLogger(Tasks.class.getName()).log(Level.SEVERE, null, ex);
                                    exportMessage = ex.getLocalizedMessage();
                                    showDialog(ERROR_DIALOG);
                                }
                                break;
                            case 3: // PREFERENCES
                                Intent intent = new Intent(Tasks.this, Preferences.class);
                                startActivityForResult(intent,PREFERENCES);
                                break;
                            case 4: // HELP:
                                showDialog(HELP);
                                break;
                            default:
                                break;
                        }
                    }
                }).create();
        }
        return null;
    }

    // TODO: This could be done better...
    private static final String dbPath = "/data/data/net.ser1.timetracker/databases/timetracker.db";
    private static final String dbBackup = "/sdcard/timetracker.db";

    private void copyDbToSd() throws IOException, IllegalArgumentException {
        InputStream in = null;
        OutputStream out = null;

        try {
            in = new BufferedInputStream(new FileInputStream(dbPath));
            out = new BufferedOutputStream(new FileOutputStream(dbBackup));
            for (int c = in.read(); c != -1; c = in.read()) {
                out.write(c);
            }
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } finally {
            in.close();
            out.close();
        }
    }

    /**
     * Creates a dialog to change the dates for which task times are shown.
     * Offers a short selection of pre-defined defaults, and the option to
     * choose a range from a dialog.
     * 
     * @see arrays.xml
     * @return the dialog to be displayed
     */
    private Dialog openChangeViewDialog() {
        return new AlertDialog.Builder(Tasks.this).setItems(R.array.views, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(VIEW_MODE, which);
                ed.commit();
                if (which == 5) {
                    Calendar calInstance = Calendar.getInstance();
                    new DatePickerDialog(Tasks.this,
                            new DatePickerDialog.OnDateSetListener() {

                                public void onDateSet(DatePicker view, int year,
                                        int monthOfYear, int dayOfMonth) {
                                    Calendar start = Calendar.getInstance();
                                    start.set(Calendar.YEAR, year);
                                    start.set(Calendar.MONTH, monthOfYear);
                                    start.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                    start.set(Calendar.HOUR, start.getMinimum(Calendar.HOUR));
                                    start.set(Calendar.MINUTE, start.getMinimum(Calendar.MINUTE));
                                    start.set(Calendar.SECOND, start.getMinimum(Calendar.SECOND));
                                    start.set(Calendar.MILLISECOND, start.getMinimum(Calendar.MILLISECOND));
                                    SharedPreferences.Editor ed = preferences.edit();
                                    ed.putLong(START_DATE, start.getTime().getTime());
                                    ed.commit();

                                    new DatePickerDialog(Tasks.this,
                                            new DatePickerDialog.OnDateSetListener() {

                                                public void onDateSet(DatePicker view, int year,
                                                        int monthOfYear, int dayOfMonth) {
                                                    Calendar end = Calendar.getInstance();
                                                    end.set(Calendar.YEAR, year);
                                                    end.set(Calendar.MONTH, monthOfYear);
                                                    end.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                                    end.set(Calendar.HOUR, end.getMaximum(Calendar.HOUR));
                                                    end.set(Calendar.MINUTE, end.getMaximum(Calendar.MINUTE));
                                                    end.set(Calendar.SECOND, end.getMaximum(Calendar.SECOND));
                                                    end.set(Calendar.MILLISECOND, end.getMaximum(Calendar.MILLISECOND));
                                                    SharedPreferences.Editor ed = preferences.edit();
                                                    ed.putLong(END_DATE, end.getTime().getTime());
                                                    ed.commit();
                                                    Tasks.this.switchView(5);  // Update the list view
                                                }
                                            },
                                            year,
                                            monthOfYear,
                                            dayOfMonth).show();
                                }
                            },
                            calInstance.get(Calendar.YEAR),
                            calInstance.get(Calendar.MONTH),
                            calInstance.get(Calendar.DAY_OF_MONTH)).show();
                } else {
                    switchView(which);
                }
            }
        }).create();
    }

    private void switchView(int which) {
        Calendar tw = Calendar.getInstance();
        int startDay = preferences.getInt(START_DAY, 0) + 1;
        tw.setFirstDayOfWeek(startDay);
        String ttl = getString(R.string.title,
                getResources().getStringArray(R.array.views)[which]);
        switch (which) {
            case 0: // today
                adapter.loadTasks(tw);
                break;
            case 1: // this week
                adapter.loadTasks(weekStart(tw, startDay), weekEnd(tw, startDay));
                break;
            case 2: // yesterday
                tw.add(Calendar.DAY_OF_MONTH, -1);
                adapter.loadTasks(tw);
                break;
            case 3: // last week
                tw.add(Calendar.WEEK_OF_YEAR, -1);
                adapter.loadTasks(weekStart(tw, startDay), weekEnd(tw, startDay));
                break;
            case 4: // all
                adapter.loadTasks();
                break;
            case 5: // select range
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(preferences.getLong(START_DATE, 0));
                System.err.println("START = " + start.getTime());
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(preferences.getLong(END_DATE, 0));
                System.err.println("END = " + end.getTime());
                adapter.loadTasks(start, end);
                DateFormat f = DateFormat.getDateInstance(DateFormat.SHORT);
                ttl = getString(R.string.title,
                        f.format(start.getTime()) + " - " + f.format(end.getTime()));
                break;
            default: // Unknown
                break;
        }
        baseTitle = ttl;
        setTitle();
        getListView().invalidate();
    }
    
    private void setTitle() {
        int total = 0;
        for (Task t : adapter.tasks) {
            total += t.getTotal();
        }
        setTitle(baseTitle + " " + formatTotal(total));
    }

    /**
     * Constructs a dialog for defining a new task.  If accepted, creates a new
     * task.  If cancelled, closes the dialog with no affect.
     * @return the dialog to display
     */
    private Dialog openNewTaskDialog() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.edit_task, null);
        return new AlertDialog.Builder(Tasks.this) //.setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.add_task_title).setView(textEntryView).setPositiveButton(R.string.add_task_ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                EditText textView = (EditText) textEntryView.findViewById(R.id.task_edit_name_edit);
                String name = textView.getText().toString();
                adapter.addTask(name);
                Tasks.this.getListView().invalidate();
            }
        }).setNegativeButton(android.R.string.cancel, null).create();
    }

    /**
     * Constructs a dialog for editing task attributes.  If accepted, alters
     * the task being edited.  If cancelled, dismissed the dialog with no effect.
     * @return the dialog to display
     */
    private Dialog openEditTaskDialog() {
        if (selectedTask == null) {
            return null;
        }
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.edit_task, null);
        return new AlertDialog.Builder(Tasks.this).setView(textEntryView).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                EditText textView = (EditText) textEntryView.findViewById(R.id.task_edit_name_edit);
                String name = textView.getText().toString();
                selectedTask.setTaskName(name);

                adapter.updateTask(selectedTask);

                Tasks.this.getListView().invalidate();
            }
        }).setNegativeButton(android.R.string.cancel, null).create();
    }

    /**
     * Constructs a dialog asking for confirmation for a delete request.  If
     * accepted, deletes the task.  If cancelled, closes the dialog.
     * @return the dialog to display
     */
    private Dialog openDeleteTaskDialog() {
        if (selectedTask == null) {
            return null;
        }
        String formattedMessage = getString(R.string.delete_task_message,
                selectedTask.getTaskName());
        return new AlertDialog.Builder(Tasks.this).setTitle(R.string.delete_task_title).setIcon(android.R.drawable.stat_sys_warning).setCancelable(true).setMessage(formattedMessage).setPositiveButton(R.string.delete_ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                adapter.deleteTask(selectedTask);
                Tasks.this.getListView().invalidate();
            }
        }).setNegativeButton(android.R.string.cancel, null).create();
    }
    final static String SDCARD = "/sdcard/";

    private String export() {
        // Export, then show a dialog
        String rangeName = getRangeName();
        String fname = rangeName + ".csv";
        File fout = new File(SDCARD + fname);
        // Change the file name until there's no conflict
        int counter = 0;
        while (fout.exists()) {
            fname = rangeName + "_" + counter + ".csv";
            fout = new File(SDCARD + fname);
            counter++;
        }
        try {
            OutputStream out = new FileOutputStream(fout);
            Cursor currentRange = adapter.getCurrentRange();
            CSVExporter.exportRows(out, currentRange);
            currentRange.close();

            return fname;
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
            return null;
        }
    }

    private String getRangeName() {
        if (adapter.currentRangeStart == -1) {
            return "all";
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        Date d = new Date();
        d.setTime(adapter.currentRangeStart);
        return f.format(d);
    }

    private Dialog openAboutDialog() {
        String versionName = "";
        try {
            PackageInfo pkginfo = this.getPackageManager().getPackageInfo("net.ser1.timetracker", 0);
            versionName = pkginfo.versionName;
        } catch (NameNotFoundException nnfe) {
            // Denada
        }

        String formattedVersion = getString(R.string.version, versionName);

        LayoutInflater factory = LayoutInflater.from(this);
        View about = factory.inflate(R.layout.about, null);

        TextView version = (TextView) about.findViewById(R.id.version);
        version.setText(formattedVersion);
        TextView links = (TextView) about.findViewById(R.id.usage);
        Linkify.addLinks(links, Linkify.ALL);
        links = (TextView) about.findViewById(R.id.credits);
        Linkify.addLinks(links, Linkify.ALL);

        return new AlertDialog.Builder(Tasks.this).setView(about).setPositiveButton(android.R.string.ok, null).create();
    }

    @Override
    protected void onPrepareDialog(int id, Dialog d) {
        EditText textView;
        switch (id) {
            case ADD_TASK:
                textView = (EditText) d.findViewById(R.id.task_edit_name_edit);
                textView.setText("");
                break;
            case EDIT_TASK:
                textView = (EditText) d.findViewById(R.id.task_edit_name_edit);
                textView.setText(selectedTask.getTaskName());
                break;
            default:
                break;
        }
    }

    /**
     * The view for an individial task in the list.
     */
    private class TaskView extends LinearLayout {

        /**
         * The view of the task name displayed in the list
         */
        private TextView taskName;
        /**
         * The view of the total time of the task.
         */
        private TextView total;
        private ImageView checkMark;

        public TaskView(Context context, Task t) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setPadding(5, 10, 5, 10);

            taskName = new TextView(context);
            taskName.setTextSize(fontSize);
            taskName.setText(t.getTaskName());
            addView(taskName, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 1f));

            checkMark = new ImageView(context);
            checkMark.setImageResource(R.drawable.ic_check_mark_dark);
            checkMark.setVisibility(View.INVISIBLE);
            addView(checkMark, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0f));

            total = new TextView(context);
            total.setTextSize(fontSize);
            total.setGravity(Gravity.RIGHT);
            total.setTransformationMethod(SingleLineTransformationMethod.getInstance());
            total.setText(formatTotal(t.getTotal()));
            addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0f));

            setGravity(Gravity.TOP);
            markupSelectedTask(t);
        }

        public void setTask(Task t) {
            taskName.setTextSize(fontSize);
            total.setTextSize(fontSize);
            taskName.setText(t.getTaskName());
            total.setText(formatTotal(t.getTotal()));
            markupSelectedTask(t);
        }

        private void markupSelectedTask(Task t) {
            if (t.isRunning()) {
                checkMark.setVisibility(View.VISIBLE);
            } else {
                checkMark.setVisibility(View.INVISIBLE);
            }
        }
    }
    private static final long MS_H = 3600000;
    private static final long MS_M = 60000;
    private static final long MS_S = 1000;

    static String formatTotal( long ttl ) {
        long hours = ttl / MS_H;
        long hours_in_ms = hours * MS_H;
        long minutes = (ttl - hours_in_ms) / MS_M;
        long minutes_in_ms = minutes * MS_M;
        long seconds = (ttl - hours_in_ms - minutes_in_ms) / MS_S;
        return String.format(FORMAT, hours, minutes, seconds);        
    }

    private class TaskAdapter extends BaseAdapter {

        private DBHelper dbHelper;
        protected ArrayList<Task> tasks;
        private Context savedContext;
        private long currentRangeStart;
        private long currentRangeEnd;

        public TaskAdapter(Context c) {
            savedContext = c;
            dbHelper = new DBHelper(c);
            dbHelper.getWritableDatabase();
            tasks = new ArrayList<Task>();
        }

        public void close() {
            dbHelper.close();
        }

        /**
         * Loads all tasks.
         */
        private void loadTasks() {
            currentRangeStart = currentRangeEnd = -1;
            loadTasks("", true);
        }

        protected void loadTasks(Calendar day) {
            loadTasks(day, (Calendar) day.clone());
        }

        protected void loadTasks(Calendar start, Calendar end) {
            String[] res = makeWhereClause(start, end);
            loadTasks(res[0], res[1] == null ? false : true);
        }

        /**
         * Java doesn't understand tuples, so the return value
         * of this is a hack.
         * @param start
         * @param end
         * @return a String pair hack, where the second item is null
         * for false, and non-null for true
         */
        private String[] makeWhereClause(Calendar start, Calendar end) {
            String query = "AND " + START + " < %d AND " + START + " >= %d";
            Calendar today = Calendar.getInstance();
            today.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
            today.set(Calendar.HOUR_OF_DAY, 12);
            for (int field : new int[]{Calendar.HOUR_OF_DAY, Calendar.MINUTE,
                        Calendar.SECOND,
                        Calendar.MILLISECOND}) {
                for (Calendar d : new Calendar[]{today, start, end}) {
                    d.set(field, d.getMinimum(field));
                }
            }
            end.add(Calendar.DAY_OF_MONTH, 1);
            currentRangeStart = start.getTimeInMillis();
            currentRangeEnd = end.getTimeInMillis();
            boolean loadCurrentTask = today.compareTo(start) != -1 &&
                    today.compareTo(end) != 1;
            query = String.format(query, end.getTimeInMillis(), start.getTimeInMillis());
            return new String[]{query, loadCurrentTask ? query : null};
        }

        /**
         * Load tasks, given a filter.  This overwrites any currently
         * loaded tasks in the "tasks" data structure.
         * 
         * @param whereClause A SQL where clause limiting the range of dates to
         *        load.  This must be a clause against the ranges table.
         * @param loadCurrent Whether or not to include data for currently active
         *        tasks.
         */
        private void loadTasks(String whereClause, boolean loadCurrent) {
            tasks.clear();

            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, "name");

            Task t = null;
            if (c.moveToFirst()) {
                do {
                    int tid = c.getInt(0);
                    String[] tids = {String.valueOf(tid)};
                    t = new Task(c.getString(1), tid);
                    Cursor r = db.rawQuery("SELECT SUM(end) - SUM(start) AS total FROM " + RANGES_TABLE + " WHERE " + TASK_ID + " = ? AND end NOTNULL " + whereClause, tids);
                    if (r.moveToFirst()) {
                        t.setCollapsed(r.getLong(0));
                    }
                    r.close();
                    if (loadCurrent) {
                        r = db.query(RANGES_TABLE, RANGE_COLUMNS,
                                TASK_ID + " = ? AND end ISNULL",
                                tids, null, null, null);
                        if (r.moveToFirst()) {
                            t.setStartTime(r.getLong(0));
                        }
                        r.close();
                    }
                    tasks.add(t);
                } while (c.moveToNext());
            }
            c.close();
            running = findCurrentlyActive().hasNext();
            notifyDataSetChanged();
        }

        /**
         * Don't forget to close the cursor!!
         * @return
         */
        protected Cursor getCurrentRange() {
            String[] res = {""};
            if (currentRangeStart != -1 && currentRangeEnd != -1) {
                Calendar start = Calendar.getInstance();
                start.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
                start.setTimeInMillis(currentRangeStart);
                Calendar end = Calendar.getInstance();
                end.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
                end.setTimeInMillis(currentRangeEnd);
                res = makeWhereClause(start, end);
            }
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor r = db.rawQuery("SELECT t.name, r.start, r.end " +
                    " FROM " + TASK_TABLE + " t, " + RANGES_TABLE + " r " +
                    " WHERE r." + TASK_ID + " = t.ROWID " + res[0] +
                    " ORDER BY t.name, r.start ASC", null);
            return r;
        }

        public Iterator<Task> findCurrentlyActive() {
            return new Iterator<Task>() {
                Iterator<Task> iter = tasks.iterator();
                Task next = null;
                public boolean hasNext() {
                    if (next != null) return true;
                    while (iter.hasNext()) {
                        Task t = iter.next();
                        if (t.isRunning()) {
                            next = t;
                            return true;
                        }
                    }
                    return false;
                }
                public Task next() {
                    if (hasNext()) {
                        Task t = next;
                        next = null;
                        return t;
                    }
                    throw new NoSuchElementException();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }                
            };
        }

        protected void addTask(String taskName) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, taskName);
            long id = db.insert(TASK_TABLE, NAME, values);
            Task t = new Task(taskName, (int) id);
            tasks.add(t);
            Collections.sort(tasks);
            notifyDataSetChanged();
        }

        protected void updateTask(Task t) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, t.getTaskName());
            String id = String.valueOf(t.getId());
            String[] vals = {id};
            db.update(TASK_TABLE, values, "ROWID = ?", vals);

            if (t.getStartTime() != NULL) {
                values.clear();
                long startTime = t.getStartTime();
                values.put(START, startTime);
                vals = new String[]{id, String.valueOf(startTime)};
                if (t.getEndTime() != NULL) {
                    values.put(END, t.getEndTime());
                }
                // If an update fails, then this is an insert
                if (db.update(RANGES_TABLE, values, TASK_ID + " = ? AND " + START + " = ?", vals) == 0) {
                    values.put(TASK_ID, t.getId());
                    db.insert(RANGES_TABLE, END, values);
                }
            }

            Collections.sort(tasks);
            notifyDataSetChanged();
        }

        public void deleteTask(Task t) {
            tasks.remove(t);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String[] id = {String.valueOf(t.getId())};
            db.delete(TASK_TABLE, "ROWID = ?", id);
            db.delete(RANGES_TABLE, TASK_ID + " = ?", id);
            notifyDataSetChanged();
        }

        public int getCount() {
            return tasks.size();
        }

        public Object getItem(int position) {
            return tasks.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TaskView view = null;
            if (convertView == null) {
                Object item = getItem(position);
                if (item != null) {
                    view = new TaskView(savedContext, (Task) item);
                }
            } else {
                view = (TaskView) convertView;
                Object item = getItem(position);
                if (item != null) {
                    view.setTask((Task) item);
                }
            }
            return view;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (vibrateClick) vibrateAgent.vibrate(100);
        if (playClick) {
            try {
                //clickPlayer.prepare();
                clickPlayer.start();
            } catch (Exception exception) {
                // Ignore this; it is probably because the media isn't yet ready.
                // There's nothing the user can do about it.
                // ignore this.  There's nothing the user can do about it.
                Logger.getLogger("TimeTracker").log(Level.INFO,
                        "Failed to play audio: "
                        +exception.getMessage());
            }
        }

        // Stop the update.  If a task is already running and we're stopping
        // the timer, it'll stay stopped.  If a task is already running and 
        // we're switching to a new task, or if nothing is running and we're
        // starting a new timer, then it'll be restarted.

        Object item = getListView().getItemAtPosition(position);
        if (item != null) {
            Task selected = (Task) item;
            if (!concurrency) {
                boolean startSelected = !selected.isRunning();
                if (running) {
                    running = false;
                    timer.removeCallbacks(updater);
                    // Disable currently running tasks
                    for (Iterator<Task> iter = adapter.findCurrentlyActive();
                         iter.hasNext();) {
                        Task t = iter.next();
                        t.stop();
                        adapter.updateTask(t);
                    }
                }
                if (startSelected) {
                    selected.start();
                    running = true;
                    timer.post(updater);
                }
            } else {
                if (selected.isRunning()) {
                    selected.stop();
                    running = adapter.findCurrentlyActive().hasNext();
                    if (!running) timer.removeCallbacks(updater);
                } else {
                    selected.start();
                    if (!running) {
                        running = true;
                        timer.post(updater);
                    }
                }
            }
            adapter.updateTask(selected);
        }
        getListView().invalidate();
        super.onListItemClick(l, v, position, id);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREFERENCES) {
            Bundle extras = data.getExtras();
            if (extras.getBoolean(START_DAY)) {
                switchView(preferences.getInt(VIEW_MODE, 0));
            }
            if (extras.getBoolean(MILITARY)) {
                if (preferences.getBoolean(MILITARY, true)) {
                    TimeRange.FORMAT = new SimpleDateFormat("HH:mm");
                } else {
                    TimeRange.FORMAT = new SimpleDateFormat("hh:mm a");
                }
            }
            if (extras.getBoolean(CONCURRENT)) {
                concurrency = preferences.getBoolean(CONCURRENT, false);
            }
            if (extras.getBoolean(SOUND)) {
                playClick = preferences.getBoolean(SOUND, false);
                if (playClick && clickPlayer == null) {
                    clickPlayer = MediaPlayer.create(this, R.raw.click);
                    try {
                        clickPlayer.prepareAsync();
                        clickPlayer.setVolume(1, 1);
                    } catch (IllegalStateException illegalStateException) {
                        // ignore this.  There's nothing the user can do about it.
                        Logger.getLogger("TimeTracker").log(Level.SEVERE,
                                "Failed to set up audio player: "
                                +illegalStateException.getMessage());
                    }
                }
            }
            if (extras.getBoolean(VIBRATE)) {
                vibrateClick = preferences.getBoolean(VIBRATE, true);
            }
            if (extras.getBoolean(FONTSIZE)) {
                fontSize = preferences.getInt(FONTSIZE, 16);
            }
        }

        if (getListView() != null) {
            getListView().invalidate();
        }
    }
}
