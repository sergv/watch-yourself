package net.ser1.timetracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class Tasks extends ListActivity  {
    private static final String TIME_FORMAT = "%02d:%02d:%02d";
    private static final int REFRESH_MS = 1000; // 60000
    private TaskAdapter adapter;
    private Handler timer;
    private Task currentlySelected = null;

    enum TaskMenu { AddTask, EditTask, DeleteTask }

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
        registerForContextMenu(getListView());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem add = menu.add(0, TaskMenu.AddTask.ordinal(), 0, "Add Task");
        add.setIcon(android.R.drawable.ic_menu_add);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        menu.setHeaderTitle("Task menu");
        menu.add(0, TaskMenu.EditTask.ordinal(), 0, "Edit Task");
        menu.add(0, TaskMenu.DeleteTask.ordinal(), 0, "Delete Task");
    }

    private Task selectedTask;
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        selectedTask = (Task)adapter.getItem((int) info.id);
        
        showDialog(item.getItemId());
        return true;
    }

    
    protected Dialog onCreateDialog(int id) {
        TaskMenu action = TaskMenu.values()[id];
        switch (action) {
        case AddTask:
            return openNewTaskDialog();
        case EditTask:
            return openEditTaskDialog();
        case DeleteTask:
            return openDeleteTaskDialog();
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
                    adapter.notifyDataSetChanged();
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
                        
                    adapter.notifyDataSetChanged();
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
            .setPositiveButton(R.string.delete_task_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    adapter.deleteTask(selectedTask);
                    adapter.notifyDataSetChanged();
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
        }
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



    private static final int DKGREEN = Color.parseColor("#005400");

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
            int bg;
            if (t.equals(currentlySelected)) {
                bg = DKGREEN;
            } else {
                bg = Color.BLACK;
            }
            setBackgroundColor(bg);
        }
    }

    
    
    private class TaskAdapter extends BaseAdapter {
        private static final String END = "end";
        private static final String START = "start";
        private static final String TASK_ID = "task_id";
        private final String[] RANGE_COLUMNS = { START, END };
        private static final String NAME = "name";
        private final String[] TASK_COLUMNS = new String[] { "ROWID", NAME };
        private DBHelper dbHelper;
        private static final String TIMETRACKER_DB_NAME = "timetracker.db";
        private static final int DBVERSION = 3;
        public static final String TASK_TABLE = "tasks";
        public static final String RANGES_TABLE = "ranges";
        private static final String TASK_NAME = "name";
        private ArrayList<Task> tasks;
        
        public TaskAdapter( Context c ) {
            savedContext = c;
            dbHelper = new DBHelper(c);
            dbHelper.getWritableDatabase();
            tasks = new ArrayList<Task>();
            loadTasks();
        }
        
        private void loadTasks() {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, "name");

            Task t = null;
            if (c.moveToFirst()) {
                do {
                    int tid = c.getInt(0);
                    String[] tids = new String[] { String.valueOf(tid) };
                    t = new Task(c.getString(1), tid );
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

        protected void addTask(String taskName) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(NAME, taskName);
            long id = db.insert(TASK_TABLE, NAME, values);
            Task t = new Task(taskName, (int)id);
            int after;
            for (after = 0; after < tasks.size(); after++) {
                if (tasks.get(after).compareTo(t) == 1) {
                    break;
                }
            }
            tasks.add( after, t );
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
        }
        
        public void deleteTask( Task t ) {
            tasks.remove(t);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String[] id = { String.valueOf(t.getId()) };
            db.delete(TASK_TABLE, "ROWID = ?", id);
            db.delete(RANGES_TABLE, TASK_ID+" = ?", id);
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

            private static final String CREATE_TASK_TABLE = 
                "CREATE TABLE %s ("
                + TASK_NAME+" TEXT COLLATE LOCALIZED NOT NULL"
                + ");";
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE ));
                db.execSQL("CREATE TABLE "+RANGES_TABLE+"("
                        + TASK_ID+" INTEGER NOT NULL,"
                        + START+" INTEGER NOT NULL,"
                        + END+" INTEGER"
                        + ");");
            }

            @Override
            public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
                if (arg1 == 2) {
                    arg0.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
                    arg0.execSQL("insert into temp(rowid,"+TASK_NAME+") select rowid,"
                            +TASK_NAME+" from "+TASK_TABLE+";");
                    arg0.execSQL("drop table "+TASK_TABLE+";");
                    arg0.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
                    arg0.execSQL("insert into "+TASK_TABLE+"(rowid,"+TASK_NAME+") select rowid,"+TASK_NAME+" from temp;");
                    arg0.execSQL("drop table temp;");
                }
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