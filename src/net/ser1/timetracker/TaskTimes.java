package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_ID;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.method.SingleLineTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TaskTimes extends ListActivity implements OnClickListener {
    private TimesAdapter adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.timelist);
        if (adapter == null) {
            adapter = new TimesAdapter(this);
            setListAdapter(adapter);
        }
        findViewById(R.id.doneButton).setOnClickListener( this );
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
        
        protected void loadTimes(int selectedTaskId) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(RANGES_TABLE, RANGE_COLUMNS, 
                    TASK_ID+" = ?", new String[] { String.valueOf(selectedTaskId) }, 
                    null, null, START);
            if (c.moveToFirst()) {
                do {
                    times.add( new TimeRange(c.getLong(0), c.getLong(1)) );
                } while (c.moveToNext());
            }
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
    }


    public void onClick(View v) {
        adapter.clear();
        finish();
    }

}
