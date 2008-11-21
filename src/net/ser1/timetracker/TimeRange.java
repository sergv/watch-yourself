package net.ser1.timetracker;

import java.util.Date;

public class TimeRange {
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
}
