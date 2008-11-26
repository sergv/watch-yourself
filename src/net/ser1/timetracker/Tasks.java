package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.NAME;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_COLUMNS;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.DBHelper.TASK_TABLE;

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
 *          SharedPreferences mPrefs = getSharedPreferences();
         mCurViewMode = mPrefs.getInt("view_mode" DAY_VIEW_MODE);
     }

     protected void onPause() {
         super.onPause();
 
         SharedPreferences.Editor ed = mPrefs.edit();
         ed.putInt("view_mode", mCurViewMode);
         ed.commit();

 * @author ser
 *
 */

public class Tasks extends ListActivity {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String TIME_FORMAT = "%02d:%02d";
    private static final int REFRESH_MS = 60000; // 60000
    private TaskAdapter adapter;
    private Handler timer;
    private TimerTask updater;
    private Task currentlySelected = null;

    enum TaskMenu { AddTask, EditTask, DeleteTask, Report, 
        ShowTimes, ChangeView, SelectStartDate, SelectEndDate }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                    System.out.println("Test");
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
        if (timer != null) {
            timer.post(updater);
        }
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
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

    private Task selectedTask;
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


    private int sYear, sMonth, sDay;
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
            return new AlertDialog.Builder(Tasks.this)
                .setItems(R.array.views, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                        case 0: // today
                            adapter.loadTasks( Calendar.getInstance(UTC) );
                            break;
                        case 1: // this week
                            Calendar tw = Calendar.getInstance(UTC);
                            adapter.loadTasks( weekStart( tw ), weekEnd( tw ) );
                            break;
                        case 2: // yesterday
                            Calendar y = Calendar.getInstance(UTC);
                            y.add(Calendar.DAY_OF_MONTH, -1);
                            adapter.loadTasks( y );
                            break;
                        case 3: // last week
                            Calendar lw = Calendar.getInstance(UTC);
                            lw.add(Calendar.DAY_OF_MONTH, -7);
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

                    private Calendar weekEnd(Calendar tw) {
                        int dow = tw.get(Calendar.DAY_OF_WEEK);
                        Calendar tw_e = (Calendar)tw.clone();
                        tw_e.add(Calendar.DAY_OF_WEEK, 6-dow);
                        return tw_e;
                    }

                    private Calendar weekStart(Calendar tw) {
                        int dow = tw.get(Calendar.DAY_OF_WEEK);
                        Calendar tw_s = (Calendar)tw.clone();
                        tw_s.add(Calendar.DAY_OF_WEEK, -dow);
                        return tw_s;
                    }
                }).create();
        case Report:
            break;
        case SelectStartDate:
            Calendar today_s = Calendar.getInstance(UTC);
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
            return new DatePickerDialog(this,
                    new DatePickerDialog.OnDateSetListener() {
                        public void onDateSet(DatePicker view, int year, 
                                int monthOfYear, int dayOfMonth) {
                            Calendar start = Calendar.getInstance(UTC);
                            start.set(Calendar.YEAR, sYear);
                            start.set(Calendar.MONTH, sMonth);
                            start.set(Calendar.DAY_OF_MONTH, sDay);
                            start.set(Calendar.HOUR, 0);
                            start.set(Calendar.MINUTE, 0);
                            start.set(Calendar.SECOND, 0);
                            start.set(Calendar.MILLISECOND, 0);
                            Calendar end = Calendar.getInstance(UTC);
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
                    }, 
                    sYear, 
                    sMonth, 
                    sDay);
        }
        return null;
    }
    
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
    
    private Dialog openDeleteTaskDialog() {
        String deleteMessage = getString(R.string.delete_task_message);
        String formattedMessage = String.format(deleteMessage, selectedTask.getTaskName());
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
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        TaskMenu t = TaskMenu.values()[item.getItemId()];
        switch (t) {
        case AddTask:
        case ChangeView:
        case Report:
            showDialog(item.getItemId());
            break;
        default:
            // Ignore the other menu items; they're context menu
            break;
        }
        return super.onMenuItemSelected(featureId, item);
    }



    private class TaskView extends LinearLayout {
        private TextView taskName;
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
            formatTotal( total, t );
            addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0.0f));
            
            markupSelectedTask(t);
        }

        private static final long MS_H = 3600000;
        private static final long MS_M = 60000;
        private static final long MS_S = 1000;
        private void formatTotal(TextView totalView, Task t ) {
            long total = t.getTotal();
            long hours = total / MS_H;
            long hours_in_ms = hours * MS_H;
            long minutes = (total - hours_in_ms) / MS_M;
            long minutes_in_ms = minutes * MS_M;
            long seconds = (total - hours_in_ms - minutes_in_ms) / MS_S;
            String fmt = String.format(TIME_FORMAT, hours, minutes, seconds);
            totalView.setText(fmt);
        }

        public void setTask(Task t) {
            taskName.setText(t.getTaskName());
            formatTotal( total, t );
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
            loadTasks( Calendar.getInstance(UTC) ) ;
        }
        
        private void loadTasks() {
            loadTasks("", true);
        }
        
        protected void loadTasks( Calendar day ) {
            loadTasks( day, (Calendar)day.clone() );
        }
        
        protected void loadTasks( Calendar start, Calendar end ) {
            String query = "AND start >= %d AND end < %d";
            Calendar today = Calendar.getInstance(UTC);
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
            } else {
                currentlySelected = selected;
                currentlySelected.start();
                adapter.updateTask(selected);
            }
        }
        getListView().invalidate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Tasks.this.getListView().invalidate();
    }
}
