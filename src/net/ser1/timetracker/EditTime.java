/**
 * TimeTracker 
 * ©2008 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.DatePicker;
import android.widget.TimePicker;

public class EditTime extends Activity implements OnClickListener {

    protected static final String END_DATE = "end-date";
    protected static final String START_DATE = "start-date";
    protected static final String CLEAR = "clear";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_time_range);
        findViewById(R.id.accept).setOnClickListener(this);
        findViewById(R.id.time_edit_cancel).setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        DatePicker startDate = (DatePicker)findViewById(R.id.start_date);
        DatePicker endDate = (DatePicker)findViewById(R.id.end_date);
        TimePicker startTime = (TimePicker)findViewById(R.id.start_time);
        TimePicker endTime = (TimePicker)findViewById(R.id.end_time);
        
        Calendar sd = Calendar.getInstance(), 
                 ed = Calendar.getInstance();
        if (getIntent().getExtras().getBoolean(CLEAR)) {
            sd = Calendar.getInstance();
            ed = sd;
        } else {
            sd.setTimeInMillis(getIntent().getExtras().getLong(START_DATE));
            ed.setTimeInMillis(getIntent().getExtras().getLong(END_DATE));
        }
        startDate.updateDate(sd.get(Calendar.YEAR), sd.get(Calendar.MONTH),
                sd.get(Calendar.DAY_OF_MONTH));
        endDate.updateDate(ed.get(Calendar.YEAR), ed.get(Calendar.MONTH),
                ed.get(Calendar.DAY_OF_MONTH));
        startTime.setCurrentHour(sd.get(Calendar.HOUR_OF_DAY));
        startTime.setCurrentMinute(sd.get(Calendar.MINUTE));
        endTime.setCurrentHour(ed.get(Calendar.HOUR_OF_DAY));
        endTime.setCurrentMinute(ed.get(Calendar.MINUTE));
        super.onResume();
    }

    public void onClick(View v) {
        DatePicker startDate = (DatePicker)findViewById(R.id.start_date);
        DatePicker endDate = (DatePicker)findViewById(R.id.end_date);
        TimePicker startTime = (TimePicker)findViewById(R.id.start_time);
        TimePicker endTime = (TimePicker)findViewById(R.id.end_time);
        Calendar s = Calendar.getInstance(),
                 e = Calendar.getInstance();
        s.set(startDate.getYear(), startDate.getMonth(), startDate.getDayOfMonth(),
                startTime.getCurrentHour(), startTime.getCurrentMinute());
        e.set(endDate.getYear(), endDate.getMonth(), endDate.getDayOfMonth(),
                endTime.getCurrentHour(), endTime.getCurrentMinute());
        if (e.compareTo(s) < 1) {
            showDialog(0);
            return;
        }
        getIntent().putExtra(START_DATE, s.getTime().getTime());
        getIntent().putExtra(END_DATE, e.getTime().getTime());
        setResult(Activity.RESULT_OK, getIntent());
        finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        return new AlertDialog.Builder(this)
            .setTitle(R.string.range_error_title)
            .setIcon(android.R.drawable.stat_sys_warning)
            .setCancelable(true)
            .setMessage(R.string.end_not_greater_than_start)
            .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            })
            .create();
    }
}
