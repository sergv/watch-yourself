package net.ser1.timetracker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import net.ser1.timetracker.Task.Priority;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class Tasks extends ListActivity {
    private static final String TIME_FORMAT = "%02d:%02d:%02d";
    private static final int REFRESH_MS = 1000; // 60000
    private TaskAdapter adapter;
    private Handler timer;
    private Task currentlySelected = null;

    enum TaskMenu { AddTask }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new TaskAdapter(this);
        setListAdapter(adapter);
        currentlySelected = adapter.findCurrentlyActive();
        timer = new Handler();
        timer.postDelayed(new TimerTask() {
            @Override
            public void run() {
                if (currentlySelected != null) {
                    adapter.notifyDataSetChanged();
                    Tasks.this.getListView().invalidate();
                }
                timer.postDelayed( this, REFRESH_MS );
            }
            
        }, REFRESH_MS );
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, TaskMenu.AddTask.ordinal(), 0, "Add Task");
        return true;
    }
    
    protected Dialog onCreateDialog(int id) {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.new_task, null);
        return new AlertDialog.Builder(Tasks.this)
            //.setIcon(R.drawable.alert_dialog_icon)
            //.setTitle(R.string.task_name)
            .setView(textEntryView)
            .setPositiveButton(R.string.task_name_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText textView = (EditText)textEntryView.findViewById(R.id.task_name_edit);
                    String name = textView.getText().toString();
                    adapter.addTask(name, Task.Priority.Medium);
                    adapter.notifyDataSetChanged();
                    Tasks.this.getListView().invalidate();
                }
            })
            .setNegativeButton(R.string.task_name_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // NADA
                }
            })
            .create();
    }
    
    @Override
    protected void onPrepareDialog( int id, Dialog d ) {
        EditText textView = (EditText)d.findViewById(R.id.task_name_edit);
        textView.setText("");
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        TaskMenu selected = TaskMenu.values()[item.getItemId()];
        switch (selected) {
        case AddTask:
            showDialog(0);
            break;
        default:
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
            setPadding(10,20,10,20);
            
            taskName = new TextView(context);
            taskName.setText(t.getTaskName());
            addView(taskName, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0.9f));

            total = new TextView(context);
            formatTotal( total, t );
            addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0.1f));
            
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
                setBackgroundColor(Color.DKGRAY);
            } else {
                setBackgroundColor(Color.BLACK);
            }
        }
    }

    
    
    private class TaskAdapter extends BaseAdapter {
        private static final String END = "end";
        private static final String START = "start";
        private static final String TASK_ID = "task_id";
        private final String[] RANGE_COLUMNS = { START, END };
        private static final String PRIORITY = "priority";
        private static final String NAME = "name";
        private final String[] TASK_COLUMNS = new String[] { "ROWID", NAME, PRIORITY };
        private DBHelper dbHelper;
        private static final String TIMETRACKER_DB_NAME = "timetracker.db";
        private static final int DBVERSION = 2;
        public static final String TASK_TABLE = "tasks";
        public static final String RANGES_TABLE = "ranges";
        private List<Task> tasks;
        
        public TaskAdapter( Context c ) {
            savedContext = c;
            dbHelper = new DBHelper(c);
            tasks = new ArrayList<Task>();
            loadTasks();
        }
        
        private void loadTasks() {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, null);

            Task t = null;
            if (c.moveToFirst()) {
                do {
                    int tid = c.getInt(0);
                    String[] tids = new String[] { String.valueOf(tid) };
                    t = new Task(c.getString(1), 
                            tid, 
                            Task.Priority.values()[c.getInt(2)]);
                    Cursor r = db.rawQuery("SELECT SUM(end) - SUM(start) AS total FROM "
                            + RANGES_TABLE+" WHERE "+TASK_ID+" = ? AND end NOTNULL" , 
                            tids );
                    if (r.moveToFirst()) {
                        t.setCollapsed(r.getLong(0));
                    }
                    r.close();
                    r = db.query(RANGES_TABLE, RANGE_COLUMNS, 
                            TASK_ID+" = ? AND end ISNULL", 
                            tids, null, null, null);
                    if (r.moveToFirst()) {
                        t.setStartTime(new Date(r.getLong(0)));
                    }
                    r.close();
                    tasks.add(t);
                } while (c.moveToNext());
            }
            c.close();
        }
        
        public Task findCurrentlyActive() {
            for (Task cur : tasks) {
                if (cur.getEndTime() == null && cur.getStartTime() != null) 
                    return cur;
            }
            return null;
        }

        protected void addTask(String taskName, Priority priority) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, taskName);
            values.put(PRIORITY, priority.ordinal());
            long id = db.insert(TASK_TABLE, NAME, values);
            Task t = new Task(taskName, (int)id, priority);
            tasks.add( t );
        }
        
        protected void updateTask( Task t ) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, t.getTaskName());
            values.put(PRIORITY, t.getPriority().ordinal());
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
        
        private Context savedContext;
        
        
        private class DBHelper extends SQLiteOpenHelper {
            public DBHelper(Context context) {
                super( context, TIMETRACKER_DB_NAME, null, DBVERSION );
            }

            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE "+TASK_TABLE+" ("
                        + "name TEXT COLLATE LOCALIZED NOT NULL,"
                        + "priority INTEGER"
                        + ");");
                db.execSQL("CREATE TABLE "+RANGES_TABLE+"("
                        + "task_id INTEGER NOT NULL,"
                        + "start INTEGER NOT NULL,"
                        + "end INTEGER"
                        + ");");
            }

            @Override
            public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
                /*
                arg0.execSQL("DROP TABLE IF EXISTS "+TASK_TABLE);
                arg0.execSQL("DROP TABLE IF EXISTS "+RANGES_TABLE);
                onCreate(arg0);
                */
            }
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
                adapter.notifyDataSetChanged();
                getListView().invalidate();
                return;
            }
            currentlySelected = selected;
            currentlySelected.start();
            adapter.updateTask(selected);
        }
    }
}