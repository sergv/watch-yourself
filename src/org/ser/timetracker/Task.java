package org.ser.timetracker;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;

public class Task {
    private String taskName;
    private int id;
    private ArrayList<TimeRange> times;
    private Date startTime = null;
    private Date endTime = null;
    private Priority priority;
    private long collapsed;
    
    enum Priority { Low, Medium, High }
    
    public Task( String name, int id ) {
        taskName = name;
        this.id = id;
        times = new ArrayList<TimeRange>();
        setPriority(Priority.Medium);
        collapsed = 0;
    }
    
    public int getId() {
        return id;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
    
    public void addTime(TimeRange range) {
        times.add(range);
    }
    
    public Iterator<TimeRange> getTimes() {
        return times.iterator();
    }
    
    public long getTotal() {
        long sum = 0;
        for (TimeRange range : times) {
            sum += range.getTotal();
        }
        if (startTime != null && endTime == null) {
            sum += new Date().getTime() - startTime.getTime();
        }
        return sum + collapsed;
    }
    
    public void collapse() {
        for (ListIterator<TimeRange> itter = times.listIterator(); itter.hasNext(); ) {
            TimeRange range = itter.next();
            collapsed += range.getTotal();
            itter.remove();
        }
    }
    
    public void setCollapsed( long collapsed ) {
        this.collapsed = collapsed;
    }
    
    public long getCollapsed() {
        return collapsed;
    }
    
    public void start() {
        if (endTime != null || startTime == null) {
            startTime = new Date();
            endTime = null;
        }
    }

    public void stop() {
        if (endTime == null) {
            endTime = new Date();
            addTime(new TimeRange( startTime, endTime ));
        }
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Priority getPriority() {
        return priority;
    }
    
    public Iterable<TimeRange> times() {
        return times;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    public boolean equals( Task other ) {
        return other != null && other.getId() == id;
    }
}