package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.EditTime.END_DATE;
import static net.ser1.timetracker.EditTime.START_DATE;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.method.SingleLineTransformationMethod;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class TaskTimes extends ListActivity {
    private TimesAdapter adapter;
    private enum TimeMenu { AddTime, DeleteTime, EditTime }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (adapter == null) {
            adapter = new TimesAdapter(this);
            setListAdapter(adapter);
        }
        registerForContextMenu(getListView());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onResume() {
        adapter.loadTimes(getIntent().getExtras().getInt(DBHelper.TASK_ID));
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
        menu.add(0, TimeMenu.AddTime.ordinal(), 0, R.string.add_time_title)
            .setIcon(android.R.drawable.ic_menu_add);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        int id = i.getItemId();
        if (id == TimeMenu.AddTime.ordinal()) {
            Intent intent = new Intent(this, EditTime.class);
            intent.putExtra(EditTime.CLEAR, true);
            startActivityForResult(intent, id);
        }
        return super.onOptionsItemSelected(i);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        menu.setHeaderTitle("Time menu");
        menu.add(0, TimeMenu.EditTime.ordinal(), 0, "Edit Time");
        menu.add(0, TimeMenu.DeleteTime.ordinal(), 0, "Delete Time");
    }

    private TimeRange selectedRange;
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        selectedRange = (TimeRange)adapter.getItem((int) info.id);
        int id = item.getItemId();
        TimeMenu action = TimeMenu.values()[id];
        Intent intent;
        switch (action) {
        case DeleteTime:
            showDialog(item.getItemId());
            break;
        case EditTime:
            intent = new Intent(this, EditTime.class);
            intent.putExtra(EditTime.START_DATE, selectedRange.getStart());
            intent.putExtra(EditTime.END_DATE, selectedRange.getEnd());
            startActivityForResult(intent, id);
            break;
        default:
            break;
        }
        return super.onContextItemSelected(item);
    }

    protected Dialog onCreateDialog(int id) {
        if (id == TimeMenu.DeleteTime.ordinal()) {
            return openDeleteTaskDialog();
        }
        return null;
    }

    private Dialog openDeleteTaskDialog() {
        return new AlertDialog.Builder(TaskTimes.this)
            .setTitle(R.string.delete_task_title)
            .setIcon(android.R.drawable.stat_sys_warning)
            .setCancelable(true)
            .setMessage(R.string.delete_time_message)
            .setPositiveButton(R.string.delete_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    adapter.deleteTimeRange(selectedRange);
                    TaskTimes.this.getListView().invalidate();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // NADA
                }
            })
            .create();
    }
    
    
    
    private class TimesAdapter extends BaseAdapter {

        private Context savedContext;
        private DBHelper dbHelper;
        private ArrayList<TimeRange> times;

        public TimesAdapter(Context c) {
            savedContext = c;
            dbHelper = new DBHelper(c);
            dbHelper.getWritableDatabase();
            times = new ArrayList<TimeRange>();
        }
        
        public void deleteTimeRange(TimeRange range) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(RANGES_TABLE, 
                    START+" = ? AND "+END+" = ? AND "+TASK_ID+" = ?", 
                    new String[]{ 
                        String.valueOf(range.getStart()), 
                        String.valueOf(range.getEnd()), 
                        String.valueOf(getIntent().getExtras().getInt(DBHelper.TASK_ID))
            });
            times.remove(range);
            notifyDataSetChanged();
        }

        protected void loadTimes(int selectedTaskId) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(RANGES_TABLE, RANGE_COLUMNS, 
                    TASK_ID+" = ? AND end NOTNULL", new String[] { String.valueOf(selectedTaskId) }, 
                    null, null, START+","+END);
            if (c.moveToFirst()) {
                do {
                    times.add( new TimeRange(c.getLong(0), c.getLong(1)) );
                } while (c.moveToNext());
            }
            c.close();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TimeView view = null;
            if (convertView == null) {
                Object item = getItem(position);
                if (item != null) view = new TimeView(savedContext,(TimeRange)item);
            } else {
                view = (TimeView) convertView;
                Object item = getItem(position);
                if (item != null) view.setTimeRange( (TimeRange)item );
            }
            return view;
        }

        public int getCount() {
            return times.size();
        }

        public Object getItem(int position) {
            return times.get(position);
        }

        public long getItemId(int position) {
            return position;
        }
        
        
        
        private class TimeView extends LinearLayout {
            private TextView dateRange;
            private TextView total;
            
            public TimeView( Context context, TimeRange t ) {
                super(context);
                setOrientation(LinearLayout.HORIZONTAL);
                setPadding(5,10,5,10);
                
                dateRange = new TextView(context);
                dateRange.setText(t.toString());
                addView(dateRange, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 1f));

                total = new TextView(context);
                total.setGravity(Gravity.RIGHT);
                total.setTransformationMethod(SingleLineTransformationMethod.getInstance());
                formatTotal( total, t.getTotal() );
                addView(total, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0.0f));
            }

            private static final long MS_H = 3600000;
            private static final long MS_M = 60000;
            private static final long MS_S = 1000;
            private static final String TIME_FORMAT = "%02d:%02d:%02d";
            private void formatTotal(TextView totalView, long total ) {
                long hours = total / MS_H;
                long hours_in_ms = hours * MS_H;
                long minutes = (total - hours_in_ms) / MS_M;
                long minutes_in_ms = minutes * MS_M;
                long seconds = (total - hours_in_ms - minutes_in_ms) / MS_S;
                String fmt = String.format(TIME_FORMAT, hours, minutes, seconds);
                totalView.setText(fmt);
            }

            public void setTimeRange(TimeRange t) {
                dateRange.setText(t.toString());
                formatTotal( total, t.getTotal() );
            }
        }

        public void clear() {
            times.clear();
        }

        public void addTimeRange(long sd, long ed) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(TASK_ID, getIntent().getExtras().getInt(TASK_ID));
            values.put(START, sd);
            values.put(END, ed);
            db.insert(RANGES_TABLE, END, values);
            times.add(new TimeRange(sd,ed));
            Collections.sort(times);
            notifyDataSetChanged();
        }

        public void updateTimeRange(long sd, long ed, int newTaskId, TimeRange old) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(START, sd);
            values.put(END, ed);
            int currentTaskId = getIntent().getExtras().getInt(TASK_ID);
            db.update(RANGES_TABLE, values, 
                    START+"=? AND "+END+"=? AND "+TASK_ID+"=?", 
                    new String[] { String.valueOf(old.getStart()), 
                                   String.valueOf(old.getEnd()), 
                                   String.valueOf(currentTaskId) 
                                 });
            if (newTaskId != currentTaskId) {
                times.remove(old);
            } else {
                old.setStart(sd);
                old.setEnd(ed);
            }
            notifyDataSetChanged();
        }
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent intent) {
        if (resCode == Activity.RESULT_OK) {
            TimeMenu item = TimeMenu.values()[reqCode];
            long sd = intent.getExtras().getLong(START_DATE);
            long ed = intent.getExtras().getLong(END_DATE);
            switch (item) {
            case AddTime:
                adapter.addTimeRange(sd, ed);
                break;
            case EditTime:
                adapter.updateTimeRange(sd, ed, 
                        getIntent().getExtras().getInt(TASK_ID), selectedRange);
                break;
            }
        }
        this.getListView().invalidate();
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Disable previous
        selectedRange = (TimeRange)getListView().getItemAtPosition(position);
        if (selectedRange != null) {
            Intent intent = new Intent(this, EditTime.class);
            intent.putExtra(EditTime.START_DATE, selectedRange.getStart());
            intent.putExtra(EditTime.END_DATE, selectedRange.getEnd());
            startActivityForResult(intent, TimeMenu.EditTime.ordinal());
        }
    }
}
