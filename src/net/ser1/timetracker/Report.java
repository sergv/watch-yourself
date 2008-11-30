package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.NAME;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_COLUMNS;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.DBHelper.TASK_TABLE;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;



public class Report extends Activity implements OnClickListener {
    
    enum Day {
        MONDAY( Calendar.MONDAY, "Mon" ), 
        TUESDAY( Calendar.TUESDAY, "Tue" ), 
        WEDNESDAY( Calendar.WEDNESDAY, "Wed"), 
        THURSDAY( Calendar.THURSDAY, "Thu"), 
        FRIDAY( Calendar.FRIDAY, "Fri"), 
        SATURDAY( Calendar.SATURDAY, "Sat"), 
        SUNDAY( Calendar.SUNDAY, "Sun");
        
        public int calEnum;
        public String header;
        Day( int calEnum, String header ) {
            this.calEnum = calEnum;
            this.header = header;
        }
        static Day fromCalEnum( int calEnum ) {
            for (Day v : values()) {
                if (v.calEnum == calEnum) return v;
            }
            return null;
        }
        public String toString() {
            return header;
        }
        public int calEnum() {
            return calEnum;
        }
    }

    /**
     * Defines how each task's time is displayed 
     */
    private Calendar week, weekEnd;
    private Map<Integer,TextView[]> dateViews = new TreeMap<Integer,TextView[]>();
    private static final int PAD = 2;
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm");
    private TextView weekView;
    private static final SimpleDateFormat WEEK_FORMAT = new SimpleDateFormat("w");
    private static final SimpleDateFormat TITLE_FORMAT = new SimpleDateFormat("EEE, MMM d");
    private DBHelper dbHelper;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.report);
        TableLayout mainReport = (TableLayout)findViewById(R.id.report);
        
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(getIntent().getExtras().getLong("report-date"));
        week = weekStart(c);
        weekEnd = weekEnd(c);
        String beginning = TITLE_FORMAT.format(week.getTime());
        String ending = TITLE_FORMAT.format(weekEnd.getTime());
        String title = getString(R.string.report_title, beginning, ending);
        setTitle( title );

        createHeader( mainReport );
        dbHelper = new DBHelper(this);
        db = dbHelper.getReadableDatabase();
        
        weekView = (TextView)findViewById(R.id.week);
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(c.getTime())));
        
        ((ImageButton)findViewById(R.id.decrement_week)).setOnClickListener(this);
        ((ImageButton)findViewById(R.id.increment_week)).setOnClickListener(this);
        
        createReport( mainReport );
        fillInTasksAndRanges();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        db = dbHelper.getReadableDatabase();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        db.close();
    }
    
    private void createHeader(TableLayout mainReport) {
        TableRow row = new TableRow(this);
        mainReport.addView(row, new TableLayout.LayoutParams());

        TextView blank = new TextView(this);
        blank.setText("Task");
        blank.setPadding(PAD,PAD,PAD,PAD);
        blank.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        row.addView(blank, new TableRow.LayoutParams(0));

        for (int i = week.getMinimum(Calendar.DAY_OF_WEEK); 
                 i <= week.getMaximum(Calendar.DAY_OF_WEEK);
                 i++) {
            Day s = Day.fromCalEnum(i);
            TextView header  = new TextView(this);
            header.setText(s.toString());
            header.setPadding(PAD,PAD,PAD,PAD);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            header.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            row.addView(header,new TableRow.LayoutParams());
        }
        
        TextView total = new TextView(this);
        total.setText("Ttl");
        total.setPadding(PAD,PAD,PAD,PAD+2);
        total.setGravity(Gravity.CENTER_HORIZONTAL);
        total.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        row.addView(total, new TableRow.LayoutParams());
    }

    private void createReport( TableLayout mainReport ) {
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, NAME);
        if (c.moveToFirst()) {
            do {
                int tid = c.getInt(0);
                TextView[] arryForDay = new TextView[8];

                dateViews.put(tid, arryForDay);
                
                TableRow row = new TableRow(this);
                mainReport.addView(row, new TableLayout.LayoutParams());
                
                TextView taskName = new TextView(this);
                taskName.setText(c.getString(1));
                taskName.setPadding(PAD,PAD,PAD,PAD);
                row.addView(taskName, new TableRow.LayoutParams(0));
                
                int dayTotal = 0;
                for (int i = week.getMinimum(Calendar.DAY_OF_WEEK); 
                         i <= week.getMaximum(Calendar.DAY_OF_WEEK);
                         i++) {
                    TextView dayTime = new TextView(this);
                    arryForDay[i-1] = dayTime;
                    dayTime.setPadding(PAD,PAD,PAD,PAD);
                    row.addView(dayTime, new TableRow.LayoutParams());
                }
    
                TextView total = new TextView(this);
                arryForDay[7] = total;
                total.setText(FORMAT.format(new Date(dayTotal)));
                total.setPadding(PAD,PAD,PAD,PAD);
                total.setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC);
                row.addView(total, new TableRow.LayoutParams());
            } while (c.moveToNext());
        }
    }
        
    /**
     * Calculates the date/time of the beginning of the week in 
     * which the supplied calendar data falls
     * @param tw the day for which to calculate the week start
     * @return a Calendar marking the start of the week
     */
    public static Calendar weekStart(Calendar tw) {
        Calendar ws = (Calendar)tw.clone();
        ws.set(Calendar.DAY_OF_WEEK, ws.getMinimum(Calendar.DAY_OF_WEEK));
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
    public static Calendar weekEnd(Calendar tw) {
        Calendar ws = (Calendar)tw.clone();
        ws.set(Calendar.DAY_OF_WEEK, ws.getMaximum(Calendar.DAY_OF_WEEK));
        ws.set(Calendar.HOUR_OF_DAY, ws.getMaximum(Calendar.HOUR_OF_DAY));
        ws.set(Calendar.MINUTE, ws.getMaximum(Calendar.MINUTE));
        ws.set(Calendar.SECOND, ws.getMaximum(Calendar.SECOND));
        ws.set(Calendar.MILLISECOND, ws.getMaximum(Calendar.MILLISECOND));
        return ws;
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.increment_week:
            week.add(Calendar.WEEK_OF_YEAR, 1);
            weekEnd.add(Calendar.WEEK_OF_YEAR, 1);
            break;
        case R.id.decrement_week:
            week.add(Calendar.WEEK_OF_YEAR, -1);
            weekEnd.add(Calendar.WEEK_OF_YEAR, -1);
            break;
        default:
            break;
        }
        String beginning = TITLE_FORMAT.format(week.getTime());
        String ending = TITLE_FORMAT.format(weekEnd.getTime());
        String title = getString(R.string.report_title, beginning, ending);
        setTitle( title );
        fillInTasksAndRanges();
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(week.getTime())));
    }
    
    private void fillInTasksAndRanges() {
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, NAME);
        // This is a re-usable instance for the overlap method, which changes 
        // the instance data.  This is here for optimization.
        Calendar day = Calendar.getInstance();

        if (c.moveToFirst()) {
            do {
                int tid = c.getInt(0);
                String tid_s = String.valueOf(tid);
                long days[] = {0,0,0,0,0,0,0};
                TextView[] arryForDay = dateViews.get(tid);
                
                Cursor r = db.query(RANGES_TABLE, RANGE_COLUMNS, TASK_ID+" = ? AND "
                        +START+" < ? AND "+END+" > ? AND "+END+" NOTNULL",
                        new String[] { tid_s, 
                                       String.valueOf(weekEnd.getTime().getTime()),
                                       String.valueOf(week.getTime().getTime())},
                        null,null,null);

                if (r.moveToFirst()) {
                    do {
                        long start = r.getLong(0);
                        long end = r.getLong(1);
                        
                        day.setTimeInMillis(end);
                        int endWeekDay = 
                            day.get(Calendar.WEEK_OF_YEAR) == week.get(Calendar.WEEK_OF_YEAR) ?
                            day.get(Calendar.DAY_OF_WEEK) : 
                            day.getMaximum(Calendar.DAY_OF_WEEK);
                        day.setTimeInMillis(start);
                        int startWeekDay = 
                            day.get(Calendar.WEEK_OF_YEAR) == week.get(Calendar.WEEK_OF_YEAR) ?
                            day.get(Calendar.DAY_OF_WEEK) :
                            day.getMinimum(Calendar.DAY_OF_WEEK);
                        
                        // At this point, "day" must be set to the start time
                        for (int i = startWeekDay-1 ; i < endWeekDay; i++ ) {
                            Day d = Day.fromCalEnum(i+1);
                            day.set(Calendar.DAY_OF_WEEK, d.calEnum);
                            days[i] += overlap(day, start, end);
                        }
                        
                    } while (r.moveToNext());
                } 
                r.close();

                int weekTotal = 0;
                for (int i = 0 ; i < 7; i++) {
                    weekTotal += days[i];
                    arryForDay[i].setText( FORMAT.format(new Date(days[i])) );                        
                }
                arryForDay[7].setText(FORMAT.format(new Date(weekTotal)));
            } while (c.moveToNext());
        }
        c.close();
    }
    
    private static final int[] FIELDS = {
        Calendar.HOUR_OF_DAY,
        Calendar.MINUTE,
        Calendar.SECOND,
        Calendar.MILLISECOND
      };
    private long overlap( Calendar day, long start, long end ) {
        for (int x : FIELDS) day.set(x, day.getMinimum(x));
        long ms_start = day.getTime().getTime();
        day.add(Calendar.DAY_OF_MONTH, 1);
        long ms_end = day.getTime().getTime();
        
        if (ms_end < start || end < ms_start) return 0;
        
        long off_start = ms_start > start ? ms_start : start;
        long off_end   = ms_end < end ? ms_end : end;
        long off_diff  = off_end - off_start;
        return off_diff;
    }
}