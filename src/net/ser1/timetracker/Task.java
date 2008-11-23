package net.ser1.timetracker;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;

public class Task {
    private String taskName;
    private int id;
    private Date startTime = null;
    private Date endTime = null;
    private Priority priority;
    private long collapsed;
    
    enum Priority { Low, Medium, High }
    
    /**
     * Constructs a new task.
     * @param name The title of the task.  Must not be null.
     * @param id The ID of the task.  Must not be null
     */
    public Task( String name, int id ) {
        this(name,id,Priority.Medium);
    }
    
    public Task( String name, int id, Priority priority ) {
        taskName = name;
        this.id = id;
        setPriority(priority);
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
    
    public long getTotal() {
        long sum = 0;
        if (startTime != null && endTime == null) {
            sum += new Date().getTime() - startTime.getTime();
        }
        return sum + collapsed;
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
        }
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Priority getPriority() {
        return priority;
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