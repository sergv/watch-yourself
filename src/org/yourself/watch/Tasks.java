/**
 * TimeTracker
 * ©2008, 2009 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 * 2013 Sergey Vinokurov
 * @author Sergey Vinokurov <serg.foo@gmail.com>
 */
package org.yourself.watch;

import static org.yourself.watch.DBHelper.END;
import static org.yourself.watch.DBHelper.NAME;
import static org.yourself.watch.DBHelper.RANGES_TABLE;
import static org.yourself.watch.DBHelper.RANGE_COLUMNS;
import static org.yourself.watch.DBHelper.START;
import static org.yourself.watch.DBHelper.TASK_COLUMNS;
import static org.yourself.watch.DBHelper.TASK_ID;
import static org.yourself.watch.DBHelper.TASK_TABLE;
import static org.yourself.watch.TimeRange.NULL;

import org.yourself.watch.CalendarUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.Set;

import junit.framework.Assert;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.text.method.SingleLineTransformationMethod;
import android.text.util.Linkify;
// import android.util.Log;
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

/**
 * Manages and displays a list of tasks, providing the ability to edit and
 * display individual task items.
 * @author ser
 */
public class Tasks extends ListActivity {

public static final int CURRENT_TASK_NOTIFICATION_ID = 1;

public static final String TIMETRACKERPREF = "timetracker.pref";
protected static final String FONTSIZE = "font-size";
protected static final String MILITARY = "military-time";
protected static final String CONCURRENT = "concurrent-tasks";
protected static final String VIBRATE = "vibrate-enabled";

protected static final String START_DAY = "start_day";
protected static final String START_DATE = "start_date";
protected static final String END_DATE = "end_date";
protected static final String VIEW_MODE = "view_mode";
protected static final String REPORT_DATE = "report_date";
protected static final String TIMEDISPLAY = "time_display";
protected static final String SHOW_ONLY_FROM_WEEK_AGO = "show_only_from_week_ago";

// private static final String TAG = "Tasks";
/**
 * Defines how each task's time is displayed
 */
private static final String FORMAT = "%02d:%02d";
private static final String DECIMAL_FORMAT = "%02d.%02d";
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
private TimerTask displayUpdater;
/**
 * The currently active task (the one that is currently being timed).  There
 * can be only one.
 */
private boolean running = false;
private Task runningTask;
/**
 * The currently selected task when the context menu is invoked.
 */
private Task selectedTask;
private SharedPreferences preferences;
private static int fontSize = 16;
private boolean concurrency;
private boolean vibrateClick = true;
private Vibrator vibrateAgent;
private ProgressDialog progressDialog = null;
private boolean decimalFormat = false;
private NotificationManager notificationManager;

/**
 * A list of menu options, including both context and options menu items
 */
protected static final int ADD_TASK = 0,
                           EDIT_TASK = 1,  DELETE_TASK = 2,
                           SHOW_TIMES = 4, CHANGE_VIEW = 5,  SELECT_START_DATE = 6,
                           SELECT_END_DATE = 7, HELP = 8,
                           SUCCESS_DIALOG = 10,  ERROR_DIALOG = 11, SET_WEEK_START_DAY = 12,
                           MORE = 13, BACKUP = 14, PREFERENCES = 15,
                           PROGRESS_DIALOG = 16, REFRESH = 17;
// TODO: This could be done better...
private static final String dbPath = "/data/data/org.yourself.watch/databases/timetracker.db";
private static final String dbBackup = "/sdcard/timetracker.db";



@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //android.os.Debug.waitForDebugger();
    preferences = getSharedPreferences(TIMETRACKERPREF, MODE_PRIVATE);
    fontSize = preferences.getInt(FONTSIZE, 16);
    concurrency = preferences.getBoolean(CONCURRENT, false);
    if (preferences.getBoolean(MILITARY, true)) {
        TimeRange.FORMAT = new SimpleDateFormat("HH:mm");
    } else {
        TimeRange.FORMAT = new SimpleDateFormat("hh:mm a");
    }

    int which = preferences.getInt(VIEW_MODE, 0);
    if (adapter == null) {
        adapter = new TaskAdapter(this, preferences);
        setListAdapter(adapter);
        switchView(which);
    }
    if (timer == null) {
        timer = new Handler();
    }
    if (displayUpdater == null) {
        displayUpdater = new TimerTask() {
            @Override
            public void run() {
                if (running) {
                    adapter.notifyDataSetChanged();
                    setTitle();
                    Tasks.this.getListView().invalidate();
                    startCurrentTaskNotification(runningTask);
                } else {
                    stopCurrentTaskNotification();
                }
                timer.postDelayed(this, REFRESH_MS);
            }
        };
    }
    decimalFormat = preferences.getBoolean(TIMEDISPLAY, false);
    registerForContextMenu(getListView());
    if (adapter.getTasks().size() == 0) {
        showDialog(HELP);
    }
    vibrateAgent = (Vibrator)getSystemService(VIBRATOR_SERVICE);
    vibrateClick = preferences.getBoolean(VIBRATE, true);
    notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
}

@Override
protected void onPause() {
    // Log.d(TAG, "onPause");
    super.onPause();
    if (timer != null) {
        timer.removeCallbacks(displayUpdater);
    }
}

@Override
protected void onStop() {
    // Log.d(TAG, "onStop");
    adapter.savePrefs();
    if (adapter != null) {
        adapter.close();
    }
    super.onStop();
}

@Override
protected void onResume() {
    // Log.d(TAG, "onResume");
    super.onResume();
    // This is only to cause the view to reload, so that we catch
    // updates to the time list.
    int which = preferences.getInt(VIEW_MODE, 0);
    switchView(which);

    if (timer != null && running) {
        /* displayUpdater would refresh list view as needed */
        timer.post(displayUpdater);
    }
}

@Override
public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(0, ADD_TASK, 0, R.string.add_task_title).setIcon(android.R.drawable.ic_menu_add);
    menu.add(0, MORE, 2, R.string.more).setIcon(android.R.drawable.ic_menu_more);
    return true;
}

@Override
public void onCreateContextMenu(ContextMenu menu,
                                View v,
                                ContextMenuInfo menuInfo) {
    menu.setHeaderTitle("Task menu");
    menu.add(0, EDIT_TASK, 0, getText(R.string.edit_task));
    menu.add(0, DELETE_TASK, 0, getText(R.string.delete_task));
    menu.add(0, SHOW_TIMES, 0, getText(R.string.show_times));
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

private AlertDialog operationSucceed;
private AlertDialog operationFailed;

private String exportMessage;
private String baseTitle;
private String weekModeTitle;

@Override
public boolean onMenuItemSelected(int featureId, MenuItem item) {
    switch (item.getItemId()) {
    case ADD_TASK:
    case MORE:
        showDialog(item.getItemId());
        break;
    case REFRESH:
        reloadListViewTasks();
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
        operationSucceed = new AlertDialog.Builder(Tasks.this)
        .setTitle(R.string.success)
        .setIcon(android.R.drawable.stat_notify_sdcard)
        .setMessage(exportMessage)
        .setPositiveButton(android.R.string.ok, null)
        .create();
        return operationSucceed;
    case ERROR_DIALOG:
        operationFailed = new AlertDialog.Builder(Tasks.this)
        .setTitle(R.string.failure)
        .setIcon(android.R.drawable.stat_notify_sdcard)
        .setMessage(exportMessage)
        .setPositiveButton(android.R.string.ok, null)
        .create();
        return operationFailed;
    case PROGRESS_DIALOG:
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Copying records...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        return progressDialog;
    case MORE:
        return new AlertDialog.Builder(Tasks.this)
        .setItems(R.array.moreMenu, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Log.d(TAG, "more dialog, on click");
                switch (which) {
                case 0: // TOGGLE LAST WEEK MODE
                    adapter.toggleOnlyFromWeekAgoDisplay();
                    reloadListViewTasks();
                    timer.removeCallbacks(displayUpdater);
                    timer.post(displayUpdater);
                    break;
                case 1: // CHANGE_VIEW:
                    showDialog(CHANGE_VIEW);
                    break;
                case 2: // COPY DB TO SD
                    showDialog(Tasks.PROGRESS_DIALOG);
                    if (new File(dbBackup).exists()) {
                        // Find the database
                        SQLiteDatabase backupDb =
                            SQLiteDatabase.openDatabase(dbBackup,
                                                        null,
                                                        SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase appDb =
                            SQLiteDatabase.openDatabase(dbPath,
                                                        null,
                                                        SQLiteDatabase.OPEN_READONLY);
                        DBBackup backup = new DBBackup(Tasks.this, progressDialog);
                        backup.execute(appDb, backupDb);
                    } else {
                        InputStream in = null;
                        OutputStream out = null;

                        try {
                            in = new BufferedInputStream(new FileInputStream(dbPath));
                            out = new BufferedOutputStream(new FileOutputStream(dbBackup));
                            for (int c = in.read(); c != -1; c = in.read()) {
                                out.write(c);
                            }
                        } catch (Exception ex) {
                            exportMessage = ex.getLocalizedMessage();
                            // Log.d(TAG,
                            //       "Exception while copying database to sdcard: "
                            //       + exportMessage);
                            showDialog(ERROR_DIALOG);
                        } finally {
                            try { in.close(); } catch (IOException ioe) { }
                            try { out.close(); } catch (IOException ioe) { }
                        }
                    }
                    break;
                // case 3: // export view to csv - skip it
                case 4: // RESTORE FROM BACKUP
                    showDialog(Tasks.PROGRESS_DIALOG);
                    SQLiteDatabase backupDb =
                        SQLiteDatabase.openDatabase(dbBackup,
                                                    null,
                                                    SQLiteDatabase.OPEN_READONLY);
                    SQLiteDatabase appDb =
                        SQLiteDatabase.openDatabase(dbPath,
                                                    null,
                                                    SQLiteDatabase.OPEN_READWRITE);
                    DBBackup backup = new DBBackup(Tasks.this, progressDialog);
                    backup.execute(backupDb, appDb);
                    break;
                case 5: // PREFERENCES
                    Intent intent = new Intent(Tasks.this, Preferences.class);
                    startActivityForResult(intent, PREFERENCES);
                    break;
                case 6: // HELP:
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

protected void notifySuccessFailure(String message, int success_string, int fail_string) {
    if (message != null) {
        exportMessage = getString(success_string, message);
        if (operationSucceed != null) {
            operationSucceed.setMessage(exportMessage);
        }
        showDialog(SUCCESS_DIALOG);
    } else {
        exportMessage = getString(fail_string, message);
        if (operationFailed != null) {
            operationFailed.setMessage(exportMessage);
        }
        showDialog(ERROR_DIALOG);
    }
}

/**
 * Creates a progressDialog to change the dates for which task times are shown.
 * Offers a short selection of pre-defined defaults, and the option to
 * choose a range from a progressDialog.
 *
 * @see arrays.xml
 * @return the progressDialog to be displayed
 */
private Dialog openChangeViewDialog() {
    return new AlertDialog.Builder(Tasks.this)
    .setItems(R.array.views, new DialogInterface.OnClickListener() {
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
    Calendar tw = getConfiguredCalendar();
    int startDay = preferences.getInt(START_DAY, 0) + 1;
    final String mode = getResources().getStringArray(R.array.views)[which];
    String title = getString(R.string.title, mode);
    String wmtitle = getString(R.string.week_mode_title, mode);
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
        // Log.d(TAG, "START = " + start.getTime());
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(preferences.getLong(END_DATE, 0));
        // Log.d(TAG, "END = " + end.getTime());
        adapter.loadTasks(start, end);
        DateFormat f = DateFormat.getDateInstance(DateFormat.SHORT);
        final String timerange = f.format(start.getTime()) + " - " + f.format(end.getTime());
        title = getString(R.string.title, timerange);
        wmtitle = getString(R.string.week_mode_title, timerange);
        break;
    default: // Unknown
        break;
    }
    baseTitle = title;
    weekModeTitle = wmtitle;
    setTitle();
    getListView().invalidate();
}

private void setTitle() {
    long total = 0;
    for (Task t : adapter.getTasks()) {
        total += t.getTotalTime();
    }
    final String title = adapter.showingOnlyFromWeekAgo() ? weekModeTitle : baseTitle;
    setTitle(title + " " + formatTotal(decimalFormat, total));
}

/**
 * Constructs a progressDialog for defining a new task.  If accepted, creates a new
 * task.  If cancelled, closes the progressDialog with no affect.
 * @return the progressDialog to display
 */
private Dialog openNewTaskDialog() {
    LayoutInflater factory = LayoutInflater.from(this);
    final View textEntryView = factory.inflate(R.layout.edit_task, null);
    return new AlertDialog.Builder(Tasks.this) //.setIcon(R.drawable.alert_dialog_icon)
           .setTitle(R.string.add_task_title)
           .setView(textEntryView)
    .setPositiveButton(R.string.add_task_ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            EditText textView = (EditText) textEntryView.findViewById(R.id.task_edit_name_edit);
            String name = textView.getText().toString();
            adapter.addTask(name);
            Tasks.this.getListView().invalidate();
        }
    }).setNegativeButton(android.R.string.cancel, null).create();
}

/**
 * Constructs a progressDialog for editing task attributes.  If accepted, alters
 * the task being edited.  If cancelled, dismissed the progressDialog with no effect.
 * @return the progressDialog to display
 */
private Dialog openEditTaskDialog() {
    if (selectedTask == null) {
        return null;
    }
    LayoutInflater factory = LayoutInflater.from(this);
    final View textEntryView = factory.inflate(R.layout.edit_task, null);
    return new AlertDialog.Builder(Tasks.this)
        .setView(textEntryView)
        .setPositiveButton(
            android.R.string.ok,
            new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText textView = (EditText) textEntryView.findViewById(R.id.task_edit_name_edit);
                String name = textView.getText().toString();
                selectedTask.setTaskName(name);

                adapter.updateTask(selectedTask);

                Tasks.this.getListView().invalidate();
            }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .create();
}

/**
 * Constructs a progressDialog asking for confirmation for a delete request.  If
 * accepted, deletes the task.  If cancelled, closes the progressDialog.
 * @return the progressDialog to display
 */
private Dialog openDeleteTaskDialog() {
    if (selectedTask == null) {
        return null;
    }
    String formattedMessage = getString(R.string.delete_task_message,
                                        selectedTask.getTaskName());
    DialogInterface.OnClickListener onOk = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            adapter.deleteTask(selectedTask);
            Tasks.this.getListView().invalidate();
        }
    };
    return new AlertDialog.Builder(Tasks.this)
           .setTitle(R.string.delete_task_title)
           .setIcon(android.R.drawable.stat_sys_warning)
           .setCancelable(true)
           .setMessage(formattedMessage)
           .setPositiveButton(R.string.delete_ok, onOk)
           .setNegativeButton(android.R.string.cancel, null).create();
}
final static String SDCARD = "/sdcard/";

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
        PackageInfo pkginfo = this.getPackageManager().getPackageInfo("org.yourself.watch", 0);
        versionName = pkginfo.versionName;
    } catch (NameNotFoundException nnfe) {
        // Denada
    }

    String formattedVersion = getString(R.string.version, versionName);

    LayoutInflater factory = LayoutInflater.from(this);
    View about = factory.inflate(R.layout.about, null);

    TextView version = (TextView) about.findViewById(R.id.version);
    version.setText(formattedVersion);
    TextView donate = (TextView) about.findViewById(R.id.donate);
    donate.setClickable(true);
    donate.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.germane-software.com/donate.html"));
            startActivity(intent);
        }
    });
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
        total.setText(formatTotal(decimalFormat, t.getTotalTime()));
        addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0f));

        setGravity(Gravity.TOP);
        markupSelectedTask(t);
    }

    public void setTask(Task t) {
        taskName.setTextSize(fontSize);
        total.setTextSize(fontSize);
        taskName.setText(t.getTaskName());
        total.setText(formatTotal(decimalFormat, t.getTotalTime()));
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
private static final double D_M = 10.0 / 6.0;
private static final double D_S = 1.0 / 36.0;

/*
 * This is pretty stupid, but because Java doesn't support closures, we have
 * to add extra overhead (more method indirection; method calls are relatively
 * expensive) if we want to re-use code.  Notice that a call to this method
 * actually filters down through four methods before it returns.
 */
static String formatTotal(boolean decimalFormat, long ttl) {
    return formatTotal(decimalFormat, FORMAT, ttl);
}
static String formatTotal(boolean decimalFormat, String format, long ttl) {
    long hours = ttl / MS_H;
    long hours_in_ms = hours * MS_H;
    long minutes = (ttl - hours_in_ms) / MS_M;
    long minutes_in_ms = minutes * MS_M;
    long seconds = (ttl - hours_in_ms - minutes_in_ms) / MS_S;
    return formatTotal(decimalFormat, format, hours, minutes, seconds);
}
static String formatTotal(boolean decimalFormat, long hours, long minutes, long seconds) {
    return formatTotal(decimalFormat, FORMAT, hours, minutes, seconds);
}
static String formatTotal(boolean decimalFormat, String format, long hours, long minutes, long seconds) {
    if (decimalFormat) {
        format = DECIMAL_FORMAT;
        minutes = Math.round((D_M * minutes) + (D_S * seconds));
        seconds = 0;
    }
    return String.format(format, hours, minutes, seconds);
}


private ArrayList<Task> constructWeekAgoTasks(SQLiteDatabase db, ArrayList<Task> tasks) {
    Calendar calendar = getConfiguredCalendar();
    /* make it lenient to allow it to wrap around */
    calendar.setLenient(true);
    calendar.add(Calendar.DAY_OF_YEAR, -7);
    CalendarUtils.resetDayFields(calendar);
    final long weekAgoStart = calendar.getTimeInMillis();

    Set<Integer> weekAgoTaskIds = new TreeSet<Integer>();
    final String query =
        "SELECT " + TASK_ID +
        " FROM " + RANGES_TABLE +
        String.format(" WHERE start >= %d", weekAgoStart);
    Cursor weekAgoTasksCursor = db.rawQuery(query, null);
    if (weekAgoTasksCursor.moveToFirst()) {
        ArrayList<Task> weekAgoTasks = new ArrayList<Task>();
        do {
            weekAgoTaskIds.add(Integer.valueOf((weekAgoTasksCursor.getInt(0))));
        } while (weekAgoTasksCursor.moveToNext());

        for (Task task : tasks) {
            if (weekAgoTaskIds.contains(task.getId())) {
                weekAgoTasks.add(task);
            }
        }
        return weekAgoTasks;
    } else {
        /* nothing to show: no tasks started in previous week */
        return new ArrayList<Task>();
    }
}

private class TaskAdapter extends BaseAdapter {

    private final DBHelper dbHelper;
    private ArrayList<Task> tasks;
    private final Context savedContext;
    private long currentRangeStart;
    private long currentRangeEnd;

    private boolean prefsDirty;
    private final SharedPreferences preferences;
    private boolean showOnlyFromWeekAgo;
    private ArrayList<Task> weekAgoTasks;

    public TaskAdapter(Context c,
                       SharedPreferences preferences) {
        savedContext             = c;
        dbHelper                 = new DBHelper(c);
        dbHelper.getWritableDatabase();
        tasks                    = new ArrayList<Task>();
        prefsDirty               = false;
        this.preferences         = preferences;
        this.showOnlyFromWeekAgo = preferences.getBoolean(SHOW_ONLY_FROM_WEEK_AGO, false);
        weekAgoTasks             = new ArrayList<Task>();
    }

    private void updateWeekAgoTasksIfNeeded(boolean force) {
        updateWeekAgoTasksIfNeeded(dbHelper.getReadableDatabase(), force);
    }

    private void updateWeekAgoTasksIfNeeded(SQLiteDatabase db, boolean force) {
        /* update if it was filled previously, which means someone actually
           used it and would benefit from update */
        if (showOnlyFromWeekAgo || force) {
            weekAgoTasks = constructWeekAgoTasks(db, tasks);
        }
    }

    public boolean showingOnlyFromWeekAgo() {
        return showOnlyFromWeekAgo;
    }

    public void savePrefs() {
        if (prefsDirty) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SHOW_ONLY_FROM_WEEK_AGO, showOnlyFromWeekAgo);
            editor.apply();
        }
    }

    final private boolean emptyList(List<?> list) {
        return list == null || list.isEmpty();
    }

    public void toggleOnlyFromWeekAgoDisplay() {
        if (!showOnlyFromWeekAgo) {
            Assert.assertTrue(emptyList(weekAgoTasks));
            updateWeekAgoTasksIfNeeded(true);
            if (emptyList(weekAgoTasks)) {
                /* update failed, nothing to show */
                return;
            }
        } else {
            weekAgoTasks.clear();
        }
        showOnlyFromWeekAgo = !showOnlyFromWeekAgo;
        prefsDirty          = true;
    }

    public ArrayList<Task> getTasks() {
        return showOnlyFromWeekAgo ? weekAgoTasks : tasks;
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
    static final String query = "AND " + START + " >= %d AND " + START + " < %d";
    private String[] makeWhereClause(Calendar start, Calendar end) {
        // String query = "AND " + START + " < %d AND " + START + " >= %d";
        Calendar today = getConfiguredCalendar();
        today.set(Calendar.HOUR_OF_DAY, 12);
        Calendar[] days = new Calendar[] {today, start, end};
        for (Calendar d : days) {
            CalendarUtils.resetDayFields(d);
        }
        end.add(Calendar.DAY_OF_MONTH, 1);
        currentRangeStart = start.getTimeInMillis();
        currentRangeEnd = end.getTimeInMillis();
        boolean loadCurrentTask = today.compareTo(start) != -1 &&
                                  today.compareTo(end) != 1;
        String q = String.format(query,
                                 start.getTimeInMillis(),
                                 end.getTimeInMillis());
        return new String[] {q, loadCurrentTask ? q : null};
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
        // Log.d(TAG, "loadTasks, whereClause = %s, loadCurrent = %s".format(whereClause, loadCurrent));
        tasks.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor tasks_cursor = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, null);

        if (tasks_cursor.moveToFirst()) {
            do {
                int task_id       = tasks_cursor.getInt(0);
                String[] task_ids = { String.valueOf(task_id) };
                Task task         = new Task(tasks_cursor.getString(1), task_id);

                final String query =
                    "SELECT SUM(end) - SUM(start) AS total FROM " + RANGES_TABLE +
                    " WHERE " + TASK_ID + " = ? " + whereClause + " AND end NOTNULL";

                Cursor range_cursor = db.rawQuery(query, task_ids);
                if (range_cursor.moveToFirst()) {
                    task.setCollapsed(range_cursor.getLong(0));
                }
                range_cursor.close();
                if (loadCurrent) {
                    range_cursor = db.query(
                        RANGES_TABLE, RANGE_COLUMNS,
                        /* Ignore where clause here because there must be only
                           one running task at any given time and we'd like
                           to always see it, even if it's outside our
                           where-clause time range. We want to see it so
                           switching tasks won't leave other running tasks
                           behind our backs.
                        */
                        String.format("%s = ? AND end ISNULL", TASK_ID),
                        //String.format("%s = ? %s AND end ISNULL", TASK_ID, whereClause),
                        // TASK_ID + " = ? " + whereClause + " AND end ISNULL",
                        task_ids, null, null, null);
                    if (range_cursor.moveToFirst()) {
                        task.setStartTime(range_cursor.getLong(0));
                    }
                    range_cursor.close();
                }
                tasks.add(task);
            } while (tasks_cursor.moveToNext());
        }
        tasks_cursor.close();
        Collections.sort(tasks);
        Iterator<Task> active = findCurrentlyActive();
        running = active.hasNext();
        runningTask = active.hasNext() ? active.next() : null;

        updateWeekAgoTasksIfNeeded(db, false);
        notifyDataSetChanged();
    }

    /**
     * Don't forget to close the cursor!!
     * @return
     */
    protected Cursor getCurrentRange() {
        String whereClause = "";
        if (currentRangeStart != -1 && currentRangeEnd != -1) {
            Calendar start = getConfiguredCalendar();
            start.setTimeInMillis(currentRangeStart);
            Calendar end = getConfiguredCalendar();
            end.setTimeInMillis(currentRangeEnd);
            whereClause = makeWhereClause(start, end)[0];
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor r = db.rawQuery(
                       "SELECT t.name, r.start, r.end " +
                       " FROM " + TASK_TABLE + " t, " + RANGES_TABLE + " r " +
                       " WHERE r." + TASK_ID + " = t.ROWID " + whereClause +
                       " ORDER BY t.name, r.start ASC", null);
        return r;
    }

    public Iterator<Task> findCurrentlyActive() {
        return new Iterator<Task>() {
            Iterator<Task> iter = getTasks().iterator();
            Task next = null;
            public boolean hasNext() {
                if (next != null) { return true; }
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
        updateWeekAgoTasksIfNeeded(db, false);
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
            vals = new String[] {id, String.valueOf(startTime)};
            if (t.getEndTime() != NULL) {
                values.put(END, t.getEndTime());
            }
            // If an update fails, then this is an insert
            if (db.update(RANGES_TABLE,
                          values,
                          TASK_ID + " = ? AND " + START + " = ?",
                          vals) == 0) {
                values.put(TASK_ID, t.getId());
                db.insert(RANGES_TABLE, END, values);
            }
        }

        Collections.sort(tasks);
        updateWeekAgoTasksIfNeeded(db, false);
        notifyDataSetChanged();
    }

    public void deleteTask(Task t) {
        tasks.remove(t);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String[] id = {String.valueOf(t.getId())};
        db.delete(TASK_TABLE, "ROWID = ?", id);
        db.delete(RANGES_TABLE, TASK_ID + " = ?", id);
        updateWeekAgoTasksIfNeeded(db, false);
        notifyDataSetChanged();
    }

    public int getCount() {
        return getTasks().size();
    }

    public Object getItem(int position) {
        return getTasks().get(position);
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
    if (vibrateClick) { vibrateAgent.vibrate(100); }

    // Stop the update.  If a task is already running and we're stopping
    // the timer, it'll stay stopped.  If a task is already running and
    // we're switching to a new task, or if nothing is running and we're
    // starting a new timer, then it'll be restarted.

    Object item = getListView().getItemAtPosition(position);
    if (item != null) {
        Task selected = (Task) item;
        if (concurrency) {
            /* NB may be broken */
            if (selected.isRunning()) {
                selected.stop();
                running = adapter.findCurrentlyActive().hasNext();
                if (!running) {
                    timer.removeCallbacks(displayUpdater);
                }
            } else {
                selected.start();
                if (!running) {
                    running = true;
                    runningTask = selected;
                    timer.post(displayUpdater);
                }
            }
        } else {
            boolean startSelected = !selected.isRunning();
            if (running) {
                running = false;
                runningTask = null;
                timer.removeCallbacks(displayUpdater);
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
                runningTask = selected;
                timer.post(displayUpdater);
            }
        }
        adapter.updateTask(selected);
    }
    getListView().invalidate();
    super.onListItemClick(l, v, position, id);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PREFERENCES && data != null) {
        Bundle extras = data.getExtras();
        if (extras.getBoolean(START_DAY)) {
            reloadListViewTasks();
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
        if (extras.getBoolean(VIBRATE)) {
            vibrateClick = preferences.getBoolean(VIBRATE, true);
        }
        if (extras.getBoolean(FONTSIZE)) {
            fontSize = preferences.getInt(FONTSIZE, 16);
        }
        if (extras.getBoolean(TIMEDISPLAY)) {
            decimalFormat = preferences.getBoolean(TIMEDISPLAY, false);
        }
    }

    if (getListView() != null) {
        getListView().invalidate();
    }
}

protected void finishedCopy(DBBackup.Result result, String message) {
    if (result == DBBackup.Result.SUCCESS) {
        reloadListViewTasks();
        message = dbBackup;
    }
    notifySuccessFailure(message,
                         R.string.restore_success,
                         R.string.restore_failed);
}


private Calendar getConfiguredCalendar() {
    Calendar tw = Calendar.getInstance();
    tw.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
    return tw;
}

private void reloadListViewTasks() {
    // This is only to cause the view to reload, so that we catch
    // updates to the time list.
    switchView(preferences.getInt(VIEW_MODE, 0));
}

// private static class ShowNotificationTask extends AsyncTask<Void, Void, Void> {
//     private final Tasks tasks;
//     private final String taskName;
//     public ShowNotificationTask(Tasks tasks,
//                                 String taskName) {
//         super();
//         this.tasks    = tasks;
//         this.taskName = taskName;
//     }
//
//     @Override
//     protected Void doInBackground(Void... params) {
//         NotificationCompat.Builder builder =
//             new NotificationCompat.Builder(tasks)
//             .setSmallIcon(R.drawable.icon)
//             .setContentTitle("Current task")
//             .setContentText(taskName);
//         // Creates an explicit intent for an Activity in your app
//         Intent resultIntent = new Intent(tasks, Tasks.class);
//         // The stack builder object will contain an artificial back stack for the
//         // started Activity.
//         // This ensures that navigating backward from the Activity leads out of
//         // your application to the Home screen.
//         TaskStackBuilder stackBuilder = TaskStackBuilder.create(tasks);
//         // Adds the back stack for the Intent (but not the Intent itself)
//         stackBuilder.addParentStack(Tasks.class);
//         // Adds the Intent that starts the Activity to the top of the stack
//         stackBuilder.addNextIntent(resultIntent);
//         PendingIntent resultPendingIntent =
//             stackBuilder.getPendingIntent(0,
//                                           PendingIntent.FLAG_UPDATE_CURRENT);
//         builder.setContentIntent(resultPendingIntent);
//         // Builds the notification and issues it.
//         tasks.notificationManager.notify(CURRENT_TASK_NOTIFICATION_ID, builder.build());
//         return null;
//     }
// }

/* fire up notification about current task */
private void startCurrentTaskNotification(Task task) {
    Assert.assertTrue(task != null);
    // ShowNotificationTask notification =
    //     new ShowNotificationTask(this,
    //                              task.getTaskName());
    // notification.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(this)
        .setSmallIcon(R.drawable.icon)
        .setContentTitle("Current task")
        .setContentText(task.getTaskName());
      // Creates an explicit intent for an Activity in your app
    Intent resultIntent = new Intent(this, Tasks.class);
      // The stack builder object will contain an artificial back stack for the
      // started Activity.
      // This ensures that navigating backward from the Activity leads out of
      // your application to the Home screen.
    TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
      // Adds the back stack for the Intent (but not the Intent itself)
    stackBuilder.addParentStack(Tasks.class);
      // Adds the Intent that starts the Activity to the top of the stack
    stackBuilder.addNextIntent(resultIntent);
    PendingIntent resultPendingIntent =
        stackBuilder.getPendingIntent(0,
                                      PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(resultPendingIntent);
      // Builds the notification and issues it.
    notificationManager.notify(CURRENT_TASK_NOTIFICATION_ID, builder.build());

}

private void stopCurrentTaskNotification() {
    notificationManager.cancel(CURRENT_TASK_NOTIFICATION_ID);
}

/**
 * Calculates the date/time of the beginning of the week in
 * which the supplied calendar date falls
 * @param tw the day for which to calculate the week start
 * @param startDay the day on which the week starts.  This must be 1-based
 * (1 = Sunday).
 * @return a Calendar marking the start of the week
 */
public static Calendar weekStart(Calendar tw, int startDay) {
    Calendar ws = (Calendar)tw.clone();
    ws.setFirstDayOfWeek(startDay);
    // START ANDROID BUG WORKAROUND
    // Android has a broken Calendar class, so the if-statement wrapping
    // the following set() is necessary to keep Android from incorrectly
    // changing the date:
    int adjustedDay = ws.get(Calendar.DAY_OF_WEEK);
    ws.add(Calendar.DATE, -((7 - (startDay - adjustedDay)) % 7));
    // The above code _should_ be:
    // ws.set(Calendar.DAY_OF_WEEK, startDay);
    // END ANDROID BUG WORKAROUND
    ws.set(Calendar.HOUR_OF_DAY, ws.getMinimum(Calendar.HOUR_OF_DAY));
    ws.set(Calendar.MINUTE, ws.getMinimum(Calendar.MINUTE));
    ws.set(Calendar.SECOND, ws.getMinimum(Calendar.SECOND));
    ws.set(Calendar.MILLISECOND, ws.getMinimum(Calendar.MILLISECOND));
    return ws;
}

/**
 * Calculates the date/time of the end of the week in
 * which the supplied calendar data falls
 * @param tw the day for which to calculate the week end
 * @return a Calendar marking the end of the week
 */
public static Calendar weekEnd(Calendar tw, int startDay) {
    Calendar ws = (Calendar)tw.clone();
    ws.setFirstDayOfWeek(startDay);
    // START ANDROID BUG WORKAROUND
    // Android has a broken Calendar class, so the if-statement wrapping
    // the following set() is necessary to keep Android from incorrectly
    // changing the date:
    int adjustedDay = ws.get(Calendar.DAY_OF_WEEK);
    ws.add(Calendar.DATE, -((7 - (startDay - adjustedDay)) % 7));
    // The above code _should_ be:
    // ws.set(Calendar.DAY_OF_WEEK, startDay);
    // END ANDROID BUG WORKAROUND
    ws.add(Calendar.DAY_OF_WEEK, 6);
    ws.set(Calendar.HOUR_OF_DAY, ws.getMaximum(Calendar.HOUR_OF_DAY));
    ws.set(Calendar.MINUTE, ws.getMaximum(Calendar.MINUTE));
    ws.set(Calendar.SECOND, ws.getMaximum(Calendar.SECOND));
    ws.set(Calendar.MILLISECOND, ws.getMaximum(Calendar.MILLISECOND));
    return ws;
}

}
