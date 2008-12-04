/**
 * TimeTracker 
 * ©2008 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeRange implements Comparable<TimeRange> {
    private long start;
    private long end;
    public static final long NULL = -1;
    
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
        if (end == NULL) return System.currentTimeMillis() - start;
        return end - start;
    }
    
    private static final DateFormat FORMAT = new SimpleDateFormat("HH:mm");
    public String toString() {
        Date s = new Date(start);
        StringBuffer b = new StringBuffer(FORMAT.format(s));
        b.append(" - ");
        if (end != NULL) {
            b.append(FORMAT.format(new Date(end)));
        } else {
            b.append( "..." );
        }
        return b.toString();
    }
    
    public int compareTo(TimeRange another) {
        if (start < another.start) {
            return -1;
        } else if (start > another.start) {
            return 1;
        } else {
            if (end == NULL) return 1;
            if (another.end == NULL) return -1;
            if (end < another.end) {
                return -1;
            } else if (end > another.end) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
