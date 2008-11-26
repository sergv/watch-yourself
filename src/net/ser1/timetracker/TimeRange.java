package net.ser1.timetracker;

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
    
    private static final String FORMAT = "%d/%d/%02d %02d:%02d - %d/%d/%02d %02d:%02d";
    public String toString() {
        Date s = new Date(start);
        Date e = new Date(end);
        return String.format(FORMAT, 
                s.getMonth()+1, s.getDate()+1, s.getYear()-100, s.getHours(), s.getMinutes(),
                e.getMonth()+1, e.getDate()+1, e.getYear()-100, e.getHours(), e.getMinutes()
                );
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
}
