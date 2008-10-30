package org.ser.timetracker;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class Task {
    private String taskName;
    private int id;
    private ArrayList<TimeRange> times;
    private Date startTime;
    private Priority priority;
    
    enum Priority { Low, Medium, High }
    
    public Task( String name, int id ) {
        taskName = name;
        this.id = id;
        times = new ArrayList<TimeRange>();
        setPriority(Priority.Medium);
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
    // TODO: This should take a range, and filter the times
    public long getTotal() {
        long sum = 0;
        for (TimeRange range : times) {
            sum += range.getTotal();
        }
        if (startTime != null) {
            sum += new Date().getTime() - startTime.getTime();
        }
        return sum;
    }
    
    public void start() {
        if (startTime == null) {
            startTime = new Date();
        }
    }

    public void stop() {
        if (startTime != null) {
            Date currentDate = new Date();
            addTime(new TimeRange( startTime, currentDate ));
            startTime = null;
        }
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Priority getPriority() {
        return priority;
    }
}
