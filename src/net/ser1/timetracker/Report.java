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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
        
        private int calEnum;
        private String header;
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
    private Map<Task,List<TimeRange>> ranges = new TreeMap<Task,List<TimeRange>>();
    private Map<Task,TextView[]> dateViews = new TreeMap<Task,TextView[]>();
    private static final int PAD = 2;
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm");
    private TextView weekView;
    private static final SimpleDateFormat WEEK_FORMAT = new SimpleDateFormat("w");
    private static final SimpleDateFormat TITLE_FORMAT = new SimpleDateFormat("EEE, MMM d");

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
        loadTasksAndRanges();
        
        weekView = (TextView)findViewById(R.id.week);
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(c.getTime())));
        
        ((ImageButton)findViewById(R.id.decrement_week)).setOnClickListener(this);
        ((ImageButton)findViewById(R.id.increment_week)).setOnClickListener(this);
        
        createReport( mainReport );
    }
    
    private void loadTasksAndRanges() {
        DBHelper dbHelper = new DBHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, NAME);

        Task t = null;
        if (c.moveToFirst()) {
            do {
                int tid = c.getInt(0);
                String tid_s = String.valueOf(tid);
                t = new Task(c.getString(1), tid );
                
                Cursor r = db.query(RANGES_TABLE, RANGE_COLUMNS, TASK_ID+" = ? AND "
                        +START+" >= ? AND "+END+" <= ? AND "+END+" NOTNULL",
                        new String[] { tid_s, 
                                       String.valueOf(week.getTime().getTime()),
                                       String.valueOf(weekEnd.getTime().getTime())},
                        null,null,null);

                List<TimeRange> rangeList = ranges.get(t);
                if (rangeList != null) {
                    rangeList.clear();
                } else {
                    rangeList = new ArrayList<TimeRange>();
                    ranges.put(t, rangeList);                    
                }
                if (r.moveToFirst()) {
                    do {
                        TimeRange range = new TimeRange(r.getLong(0), r.getLong(1));
                        rangeList.add(range);
                    } while (r.moveToNext());
                } 
                r.close();
            } while (c.moveToNext());
        }
        c.close();
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
        
        for (int i=1; i<9; i++) {
            mainReport.setColumnStretchable(i, false);
        }
        mainReport.setColumnShrinkable(0, true);
        mainReport.setColumnStretchable(0, true);
    }

    private void createReport( TableLayout mainReport ) {
        for (Task t : ranges.keySet()) {
            TextView[] arryForDay = new TextView[8];
            dateViews.put(t, arryForDay);
            TableRow row = new TableRow(this);
            mainReport.addView(row, new TableLayout.LayoutParams());
            
            TextView taskName = new TextView(this);
            taskName.setText(t.getTaskName());
            taskName.setPadding(PAD,PAD,PAD,PAD);
            row.addView(taskName, new TableRow.LayoutParams(0));
            
            int dayTotal = 0;
            for (int i = week.getMinimum(Calendar.DAY_OF_WEEK); 
                     i <= week.getMaximum(Calendar.DAY_OF_WEEK);
                     i++) {
                Day d = Day.fromCalEnum(i);
                int sum = rangeSumForTask(t,getCalendarDay(d));
                dayTotal += sum;
                TextView dayTime = new TextView(this);
                arryForDay[i-1] = dayTime;
                dayTime.setText( FORMAT.format(new Date(sum)) );
                dayTime.setPadding(PAD,PAD,PAD,PAD);
                row.addView(dayTime, new TableRow.LayoutParams());
            }

            TextView total = new TextView(this);
            arryForDay[7] = total;
            total.setText(FORMAT.format(new Date(dayTotal)));
            total.setPadding(PAD,PAD,PAD,PAD);
            total.setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC);
            row.addView(total, new TableRow.LayoutParams());
        }
    }
        
    private Calendar getCalendarDay(Day d) {
        Calendar day = (Calendar)week.clone();
        day.set(Calendar.DAY_OF_WEEK, d.calEnum());
        return day;
    }

    private int rangeSumForTask(Task t, Calendar d) {
        int sum = 0;
        for (TimeRange r : ranges.get(t)) {
            sum += r.dayOverlap(d);
        }
        return sum;
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
        loadTasksAndRanges();
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(week.getTime())));
        String beginning = TITLE_FORMAT.format(week.getTime());
        String ending = TITLE_FORMAT.format(weekEnd.getTime());
        String title = getString(R.string.report_title, beginning, ending);
        setTitle( title );
        for (Task t : ranges.keySet()) {
            TextView[] arryForDay = dateViews.get(t);
            int dayTotal = 0;
            for (int i = week.getMinimum(Calendar.DAY_OF_WEEK); 
                     i <= week.getMaximum(Calendar.DAY_OF_WEEK);
                     i++) {
                Day d = Day.fromCalEnum(i);
                int sum = rangeSumForTask(t,getCalendarDay(d));
                dayTotal += sum;
                arryForDay[i-1].setText( FORMAT.format(new Date(sum)) );
            }
            arryForDay[7].setText(FORMAT.format(new Date(dayTotal)));
        }
    }
}
