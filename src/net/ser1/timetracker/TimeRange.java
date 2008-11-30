/**
 * TimeTracker 
 * ©2008 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimeRange implements Comparable<TimeRange> {
    private long start;
    private long end;
    
    public TimeRange( Date start, Date end ) {
        this.start = start.getTime();
        this.end = end.getTime();
    }
    public TimeRange( long start, long end ) {
        this.start = start;
        this.end = end;
    }
    public long getStart() {
        return start;
    }
    public void setStart(long start) {
        this.start = start;
    }
    public long getEnd() {
        return end;
    }
    public void setEnd(long end) {
        this.end = end;
    }
    public long getTotal() {
        return end - start;
    }
    
    private static final DateFormat FORMAT = new SimpleDateFormat("MM/dd/yy HH:mm");
    public String toString() {
        Date s = new Date(start);
        Date e = new Date(end);
        StringBuffer b = new StringBuffer(FORMAT.format(s));
        b.append(" - ");
        b.append(FORMAT.format(e));
        return b.toString();
    }
    
    public int compareTo(TimeRange another) {
        if (start < another.start) {
            return -1;
        } else if (start > another.start) {
            return 1;
        } else {
            if (end < another.end) {
                return -1;
            } else if (end > another.end) {
                return 1;
            } else {
                return 0;
            }
        }
    }
    
    private static final int[] FIELDS = {
      Calendar.HOUR_OF_DAY,
      Calendar.MINUTE,
      Calendar.SECOND,
      Calendar.MILLISECOND
    };
        
    /**
     * Returns the amount of time that occurs during a given day
     * @param d the day to find the overlapping time for
     * @return the number of milliseconds of this time range that overlap with
     * the given day 
     */
    public int dayOverlap(Calendar d) {
        d = (Calendar)d.clone();
        for (int x : FIELDS) d.set(x, d.getMinimum(x));
        long ms_start = d.getTime().getTime();
        d.add(Calendar.DAY_OF_MONTH, 1);
        long ms_end = d.getTime().getTime();
        
        if (ms_end < start || end < ms_start) return 0;
        
        long off_start = ms_start > start ? ms_start : start;
        long off_end   = ms_end < end ? ms_end : end;
        long off_diff  = off_end - off_start;
        
        // This is sort of stupid, because there are only 86400000ms in a full
        // day, and the previous logic ensures that the value will be less than
        // that amount.
        assert off_diff < Integer.MAX_VALUE;
        return (int)off_diff;
    }
}
