package org.ser.timetracker;

/**
 * TODO:
 * [x] Mark active task
 * [x] Format time display
 * [_] Load/save time from DB
 * [x] Clicking activates/de-activates time
 * [x] Add tasks
 * [_] Remove tasks
 * [_] Edit tasks
 * [_] Projects
 */

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
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
    private TaskAdapter adapter;
    private Handler timer;
    private Task currentlySelected = null;

    enum TaskMenu { AddTask }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new TaskAdapter(this);
        setListAdapter(adapter);
        timer = new Handler();
        timer.postDelayed(new TimerTask() {
            @Override
            public void run() {
                if (currentlySelected != null) {
                    adapter.notifyDataSetChanged();
                    Tasks.this.getListView().invalidate();
                }
                timer.postDelayed( this, 1000 );
            }
            
        }, 1000 );
        
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
                    adapter.addTask(name);
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
        }

        // TODO: Format the HH:MM:SS
        private void formatTotal(TextView total2, Task t ) {
            total2.setText(formatTime(t.getTotal()));
        }

        private String formatTime(long total2) {
            long hours = total2 / 360000;
            long hours_in_ms = hours * 360000;
            long minutes = (total2 - hours_in_ms) / 60000;
            long minutes_in_ms = minutes * 60000;
            long seconds = (total2 - hours_in_ms - minutes_in_ms) / 1000;
            StringBuffer rv = new StringBuffer( String.valueOf(hours) );
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        public void setTask(Task t) {
            taskName.setText(t.getTaskName());
            formatTotal( total, t );
        }
    }

    private class TaskAdapter extends BaseAdapter {
        private List<Task> tasks;
        private int nextId = 0;
        
        public TaskAdapter( Context c ) {
            savedContext = c;
            tasks = new ArrayList<Task>();
        }
        
        protected void addTask(String taskName) {
            tasks.add( new Task(taskName, nextId) );
            nextId += 1;
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
            TaskView view;
            if (convertView == null) {
                view = new TaskView(savedContext,tasks.get(position));
            } else {
                view = (TaskView) convertView;
                view.setTask( tasks.get(position) );
            }
            return view;
        }
        
        private Context savedContext;
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Disable previous
        if (currentlySelected != null) {
            currentlySelected.stop();
        }
        // Enable current
        Object item = getListView().getItemAtPosition(position);
        if (item != null) {
            Task selected = (Task)item;
            if (currentlySelected == selected) return;
            currentlySelected = selected;
            currentlySelected.start();
        }
    }

}