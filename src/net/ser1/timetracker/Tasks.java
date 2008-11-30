/**
 * TimeTracker 
 * ©2008 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.SingleLineTransformationMethod;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Manages and displays a list of tasks, providing the ability to edit and
 * display individual task items.
 * 
 * @author ser
 */
public class Tasks extends ListActivity {
    /**
     * Defines how each task's time is displayed 
     */
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm");
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
     * The callback that actually updates the display.
     */
    private TimerTask updater;
    /**
     * The currently active task (the one that is currently being timed).  There
     * can be only one.
     */
    private Task currentlySelected = null;
    /**
     * The currently selected task when the context menu is invoked.
     */
    private Task selectedTask;
    private int sYear, sMonth, sDay;

    /**
     * A list of menu options, including both context and options menu items 
     */
    enum TaskMenu { AddTask, EditTask, DeleteTask, Report, 
        ShowTimes, ChangeView, SelectStartDate, SelectEndDate }

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (adapter == null) {
            adapter = new TaskAdapter(this);
            setListAdapter(adapter);
            currentlySelected = adapter.findCurrentlyActive();
        }
        if (timer == null) {
            timer = new Handler();
        }
        if (updater == null) {
            updater = new TimerTask() {
                @Override
                public void run() {
                    Date d = new Date();
                    System.out.println(d + " -- " + d.getTimezoneOffset());
                    if (currentlySelected != null) {
                        adapter.notifyDataSetChanged();
                        Tasks.this.getListView().invalidate();
                    }
                    timer.postDelayed( this, REFRESH_MS );
                }
                
            };
        }
        registerForContextMenu(getListView());
    }
    
    @Override
    protected void onPause() {
        if (timer != null) {
            timer.removeCallbacks(updater);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (timer != null && currentlySelected != null) {
            timer.post(updater);
        }
        super.onResume();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, TaskMenu.AddTask.ordinal(), 0, R.string.add_task_title)
            .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, TaskMenu.ChangeView.ordinal(), 1, R.string.change_view_title)
            .setIcon(android.R.drawable.ic_menu_compass);
        menu.add(0, TaskMenu.Report.ordinal(), 2, R.string.generate_report_title)
            .setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        menu.setHeaderTitle("Task menu");
        menu.add(0, TaskMenu.EditTask.ordinal(), 0, "Edit Task");
        menu.add(0, TaskMenu.DeleteTask.ordinal(), 0, "Delete Task");
        menu.add(0, TaskMenu.ShowTimes.ordinal(), 0, "Show times");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        selectedTask = (Task)adapter.getItem((int) info.id);
        TaskMenu m = TaskMenu.values()[item.getItemId()];
        switch (m) {
        case ShowTimes:
            Intent intent = new Intent(this, TaskTimes.class);
            intent.putExtra(DBHelper.TASK_ID, selectedTask.getId());
            startActivity(intent);
            break;
        default:
            showDialog(item.getItemId());
            break;
        }
        return super.onContextItemSelected(item);
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        TaskMenu t = TaskMenu.values()[item.getItemId()];
        switch (t) {
        case AddTask:
        case ChangeView:
            showDialog(item.getItemId());
            break;
        case Report:
            Intent intent = new Intent(this, Report.class);
            intent.putExtra("report-date", new Date().getTime());
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
        TaskMenu action = TaskMenu.values()[id];
        switch (action) {
        case AddTask:
            return openNewTaskDialog();
        case EditTask:
            return openEditTaskDialog();
        case DeleteTask:
            return openDeleteTaskDialog();
        case ChangeView:
            return openChangeViewDialog();
        case SelectStartDate:
            Calendar today_s = Calendar.getInstance();
            // An ad-hoc date picker for the start date, which in turn
            // invokes another dialog (the "pick end date" dialog) when it is 
            // finished
            return new DatePickerDialog(this,
                    new DatePickerDialog.OnDateSetListener() {
                        public void onDateSet(DatePicker view, int year, 
                                int monthOfYear, int dayOfMonth) {
                            sYear = year;
                            sMonth = monthOfYear;
                            sDay = dayOfMonth;
                            showDialog(TaskMenu.SelectEndDate.ordinal());
                        }
                    }, 
                    today_s.get(Calendar.YEAR), 
                    today_s.get(Calendar.MONTH), 
                    today_s.get(Calendar.DAY_OF_MONTH));
        case SelectEndDate:
            // Another ad-hoc date picker for the end date.  This is invoked by
            // the start-date dialog.  When complete, loads a new list of tasks
            // filtered by the date range.
            return new DatePickerDialog(this,
                    new DatePickerDialog.OnDateSetListener() {
                        public void onDateSet(DatePicker view, int year, 
                                int monthOfYear, int dayOfMonth) {
                            Calendar start = Calendar.getInstance();
                            start.set(Calendar.YEAR, sYear);
                            start.set(Calendar.MONTH, sMonth);
                            start.set(Calendar.DAY_OF_MONTH, sDay);
                            start.set(Calendar.HOUR, 0);
                            start.set(Calendar.MINUTE, 0);
                            start.set(Calendar.SECOND, 0);
                            start.set(Calendar.MILLISECOND, 0);
                            Calendar end = Calendar.getInstance();
                            end.set(Calendar.YEAR, sYear);
                            end.set(Calendar.MONTH, sMonth);
                            end.set(Calendar.DAY_OF_MONTH, sDay);
                            end.set(Calendar.HOUR, 0);
                            end.set(Calendar.MINUTE, 0);
                            end.set(Calendar.SECOND, 0);
                            end.set(Calendar.MILLISECOND, 0);
                            end.add(Calendar.DAY_OF_MONTH, 1);
                            adapter.loadTasks( start, end );
                        }
                    }, sYear, sMonth, sDay);
        }
        // TODO: If we get here, we're in trouble; android doesn't like null dialogs.
        return null;
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
        return new AlertDialog.Builder(Tasks.this)
        .setItems(R.array.views, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0: // today
                    adapter.loadTasks( Calendar.getInstance() );
                    break;
                case 1: // this week
                    Calendar tw = Calendar.getInstance();
                    adapter.loadTasks( weekStart( tw ), weekEnd( tw ) );
                    break;
                case 2: // yesterday
                    Calendar y = Calendar.getInstance();
                    y.add(Calendar.DAY_OF_MONTH, -1);
                    adapter.loadTasks( y );
                    break;
                case 3: // last week
                    Calendar lw = Calendar.getInstance();
                    lw.add(Calendar.WEEK_OF_YEAR, -1);
                    adapter.loadTasks( weekStart( lw ), weekEnd( lw ) );
                    break;
                case 4: // all
                    adapter.loadTasks();
                    break;
                case 5: // select range
                    showDialog(TaskMenu.SelectStartDate.ordinal());
                    break;
                default: // Unknown
                    break;
                }
                Tasks.this.getListView().invalidate();
            }
        }).create();
    }

    /**
     * Constructs a dialog for defining a new task.  If accepted, creates a new
     * task.  If cancelled, closes the dialog with no affect.
     * @return the dialog to display
     */
    private Dialog openNewTaskDialog() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.edit_task, null);
        return new AlertDialog.Builder(Tasks.this)
            //.setIcon(R.drawable.alert_dialog_icon)
            .setTitle(R.string.add_task_title)
            .setView(textEntryView)
            .setPositiveButton(R.string.add_task_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText textView = (EditText)textEntryView.findViewById(R.id.task_edit_name_edit);
                    String name = textView.getText().toString();
                    adapter.addTask(name);
                    Tasks.this.getListView().invalidate();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // NADA
                }
            })
            .create();
    }

    /**
     * Constructs a dialog for editing task attributes.  If accepted, alters
     * the task being edited.  If cancelled, dismissed the dialog with no effect.
     * @return the dialog to display
     */
    private Dialog openEditTaskDialog() {
        if (selectedTask == null) return null;
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.edit_task, null);
        return new AlertDialog.Builder(Tasks.this)
            .setView(textEntryView)
            .setPositiveButton(R.string.edit_task_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText textView = (EditText)textEntryView.findViewById(R.id.task_edit_name_edit);
                    String name = textView.getText().toString();
                    selectedTask.setTaskName(name);
                    
                    adapter.updateTask(selectedTask);
                        
                    Tasks.this.getListView().invalidate();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // NADA
                }
            })
            .create();
    }
    
    /**
     * Constructs a dialog asking for confirmation for a delete request.  If
     * accepted, deletes the task.  If cancelled, closes the dialog.
     * @return the dialog to display
     */
    private Dialog openDeleteTaskDialog() {
        String formattedMessage = getString(R.string.delete_task_message, 
                selectedTask.getTaskName());
        return new AlertDialog.Builder(Tasks.this)
            .setTitle(R.string.delete_task_title)
            .setIcon(android.R.drawable.stat_sys_warning)
            .setCancelable(true)
            .setMessage(formattedMessage)
            .setPositiveButton(R.string.delete_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    adapter.deleteTask(selectedTask);
                    Tasks.this.getListView().invalidate();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // NADA
                }
            })
            .create();
    }

    @Override
    protected void onPrepareDialog( int id, Dialog d ) {
        TaskMenu action = TaskMenu.values()[id];
        EditText textView;
        switch (action) {
        case AddTask:
            textView = (EditText)d.findViewById(R.id.task_edit_name_edit);
            textView.setText("");
            break;
        case EditTask:
            textView = (EditText)d.findViewById(R.id.task_edit_name_edit);
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
        
        public TaskView( Context context, Task t ) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setPadding(5,10,5,10);
            
            taskName = new TextView(context);
            taskName.setText(t.getTaskName());
            addView(taskName, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 1f));

            total = new TextView(context);
            total.setGravity(Gravity.RIGHT);
            total.setTransformationMethod(SingleLineTransformationMethod.getInstance());
            total.setText(FORMAT.format(new Date(t.getTotal())));
            addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0.0f));
            
            markupSelectedTask(t);
        }

        public void setTask(Task t) {
            taskName.setText(t.getTaskName());
            total.setText(FORMAT.format(new Date(t.getTotal())));
            markupSelectedTask(t);
        }

        private void markupSelectedTask(Task t) {
            if (t.equals(currentlySelected)) {
                taskName.getPaint().setShadowLayer(1, 1, 1,Color.YELLOW);
                total.getPaint().setShadowLayer(1, 1, 1, Color.YELLOW);
            } else {
                taskName.getPaint().clearShadowLayer();
                total.getPaint().clearShadowLayer();
            }
        }
    }

    
    
    private class TaskAdapter extends BaseAdapter {
        private DBHelper dbHelper;
        private ArrayList<Task> tasks;
        private Context savedContext;

        public TaskAdapter( Context c ) {
            savedContext = c;
            dbHelper = new DBHelper(c);
            dbHelper.getWritableDatabase();
            tasks = new ArrayList<Task>();
            loadTasks( Calendar.getInstance() ) ;
        }
        
        private void loadTasks() {
            loadTasks("", true);
        }
        
        protected void loadTasks( Calendar day ) {
            loadTasks( day, (Calendar)day.clone() );
        }
        
        protected void loadTasks( Calendar start, Calendar end ) {
            String query = "AND start >= %d AND end < %d";
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR, 12);
            for (int field : new int[] { Calendar.HOUR, Calendar.MINUTE, 
                                         Calendar.SECOND, 
                                         Calendar.MILLISECOND }) {
                for (Calendar d : new Calendar[] { today, start, end }) {
                    d.set(field, 0);
                }
            }
            end.add(Calendar.DAY_OF_MONTH, 1);
            boolean loadCurrentTask = start.compareTo(today) != 1 &&
                                      end.compareTo(end) != -1;
            query = String.format( query, start.getTime().getTime(), 
                    end.getTime().getTime());
            loadTasks(query, loadCurrentTask);
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
        private void loadTasks( String whereClause, boolean loadCurrent ) {
            tasks.clear();
            
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, "name");

            Task t = null;
            if (c.moveToFirst()) {
                do {
                    int tid = c.getInt(0);
                    String[] tids = { String.valueOf(tid) };
                    t = new Task(c.getString(1), tid );
                    Cursor r = db.rawQuery("SELECT SUM(end) - SUM(start) AS total FROM "
                            + RANGES_TABLE+" WHERE "+TASK_ID+" = ? AND end NOTNULL " 
                            + whereClause, tids );
                    if (r.moveToFirst()) {
                        t.setCollapsed(r.getLong(0));
                    }
                    r.close();
                    if (loadCurrent) {
                        r = db.query(RANGES_TABLE, RANGE_COLUMNS, 
                                TASK_ID+" = ? AND end ISNULL", 
                                tids, null, null, null);
                        if (r.moveToFirst()) {
                            t.setStartTime(new Date(r.getLong(0)));
                        }
                        r.close();
                    }
                    tasks.add(t);
                } while (c.moveToNext());
            }
            c.close();
            notifyDataSetChanged();
        }
        
        public Task findCurrentlyActive() {
            for (Task cur : tasks) {
                if (cur.getEndTime() == null && cur.getStartTime() != null) 
                    return cur;
            }
            return null;
        }

        protected void addTask(String taskName) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, taskName);
            long id = db.insert(TASK_TABLE, NAME, values);
            Task t = new Task(taskName, (int)id);
            tasks.add( t );
            Collections.sort(tasks);
            notifyDataSetChanged();
        }
        
        protected void updateTask( Task t ) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, t.getTaskName());
            String id = String.valueOf(t.getId());
            String[] vals = { id };
            db.update(TASK_TABLE, values, "ROWID = ?", vals);
            
            if (t.getStartTime() != null) {
                values.clear();
                long startTime = t.getStartTime().getTime();
                values.put(START, startTime);
                vals = new String[] { id, String.valueOf(startTime) };
                if (t.getEndTime() != null) {
                    values.put(END, t.getEndTime().getTime());
                }
                if (db.update(RANGES_TABLE, values, TASK_ID+" = ? AND "+START+" = ?", vals) == 0) {
                    values.put(TASK_ID, t.getId());
                    values.put(END, (String)null);
                    db.insert(RANGES_TABLE, END, values);
                }
            }
            
            Collections.sort(tasks);
            notifyDataSetChanged();
        }
        
        public void deleteTask( Task t ) {
            tasks.remove(t);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String[] id = { String.valueOf(t.getId()) };
            db.delete(TASK_TABLE, "ROWID = ?", id);
            db.delete(RANGES_TABLE, TASK_ID+" = ?", id);
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
                if (item != null) view = new TaskView(savedContext,(Task)item);
            } else {
                view = (TaskView) convertView;
                Object item = getItem(position);
                if (item != null) view.setTask( (Task)item );
            }
            return view;
        }        
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Disable previous
        if (currentlySelected != null) {
            currentlySelected.stop();
            adapter.updateTask(currentlySelected);
        }
        // Enable current
        Object item = getListView().getItemAtPosition(position);
        if (item != null) {
            Task selected = (Task)item;
            if (selected.equals(currentlySelected)) {
                currentlySelected = null;
                timer.removeCallbacks(updater);
            } else {
                currentlySelected = selected;
                currentlySelected.start();
                adapter.updateTask(selected);
                timer.post(updater);
            }
        }
        getListView().invalidate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (getListView() != null) getListView().invalidate();
    }
}
