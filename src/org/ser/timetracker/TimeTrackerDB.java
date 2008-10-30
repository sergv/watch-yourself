package org.ser.timetracker;

import java.util.HashMap;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class TimeTrackerDB extends ContentProvider {
    private static final String TASK_MIME = "vnd.android.cursor.item/org.ser1.Task";
    private static final String RANGES_MIME = "vnd.android.cursor.dir/org.ser1.Range";
    private static final String TASKS_MIME = "vnd.android.cursor.dir/org.ser1.Task";
    private static final String DBNAME = "timetracker.db";
    private static final int DBVERSION = 0;
    
    private static final String TASK_TABLE = "tasks";
    private static HashMap<String, String> taskProjectionMap;
    private static final String T_ID = "_id",
        T_NAME = "name",
        T_PRIORITY = "priority";
    
    private static final String RANGES = "ranges";
    private static HashMap<String, String> rangeProjectionMap;
    private static final String R_ID = "_task_id",
        R_START = "start",
        R_END = "end";
    
    private DBHelper dbHelper = null;
    private static final UriMatcher URI_MATCHER;
    private static final Uri RANGES_URI = Uri.parse(TimeTracker.AUTHORITY+"/ranges");
    private static final Uri TASKS_URI = Uri.parse(TimeTracker.AUTHORITY+"/tasks");
    private static enum Uris {
        AllTasks, TaskId, AllRanges, RangesForTask, TaskRangesInRange, AllRangesInRange, RangeForTask
    }
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "tasks", Uris.AllTasks.ordinal());
        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "tasks/#", Uris.TaskId.ordinal());

        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "ranges", Uris.AllRanges.ordinal());
        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "ranges/#", Uris.RangesForTask.ordinal());
        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "ranches/#/#", Uris.AllRangesInRange.ordinal());
        URI_MATCHER.addURI(TimeTracker.AUTHORITY, "ranches/#/#/#", Uris.TaskRangesInRange.ordinal());

        taskProjectionMap = new HashMap<String, String>();
        taskProjectionMap.put(T_ID, T_ID);
        taskProjectionMap.put(T_NAME, T_NAME);
        taskProjectionMap.put(T_PRIORITY, T_PRIORITY);
        
        rangeProjectionMap = new HashMap<String, String>();
        rangeProjectionMap.put(R_ID, R_ID);
        rangeProjectionMap.put(R_START, R_START);
        rangeProjectionMap.put(R_END, R_END);
    }


    @Override
    public boolean onCreate() {
        dbHelper = new DBHelper(getContext());
        return true;
    }


    private class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) {
            super( context, DBNAME, null, DBVERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE "+TASK_TABLE+" ("
                    + "_id INTEGER PRIMARY KEY,"
                    + "name TEXT COLLATE LOCALIZED NOT NULL,"
                    + "priority INTEGER"
                    + ");");
            db.execSQL("CREATE TABLE "+RANGES+"("
                    + "_task_id INTEGER NOT NULL,"
                    + "start INTEGER NOT NULL,"
                    + "end INTEGER"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
            arg0.execSQL("DROP TABLE IF EXISTS "+TASK_TABLE);
            arg0.execSQL("DROP TABLE IF EXISTS "+RANGES);
            onCreate(arg0);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        // Query the database using the arguments provided
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String[] whereArgs = null;
        String orderBy = sortOrder;

        // What type of query are we going to use - the URL_MATCHER
        // defined at the bottom of this class is used to pattern-match
        // the URL and select the right query from the switch statement
        int match = URI_MATCHER.match(uri);
        List<String> arguments = uri.getPathSegments();
        int nargs = arguments.size();
        Uris selected = Uris.values()[match];
        switch (selected) {
        case AllTasks:
            qb.setTables(TASK_TABLE);
            qb.setProjectionMap(taskProjectionMap);
            break;
        case TaskId:
            qb.setTables(TASK_TABLE);
            qb.setProjectionMap(taskProjectionMap);
            qb.appendWhere("_id == ?");
            whereArgs = new String[1];
            whereArgs[0] = uri.getLastPathSegment();
            if (TextUtils.isEmpty(sortOrder)) {
                orderBy = "modified "+T_PRIORITY+","+T_NAME;
            }
            break;
        case RangesForTask:
            qb.setTables(RANGES);
            qb.setProjectionMap(rangeProjectionMap);
            qb.appendWhere("_task_id = ?");
            whereArgs = new String[1];
            whereArgs[0] = uri.getLastPathSegment();
            if (TextUtils.isEmpty(sortOrder)) {
                orderBy = "modified "+R_START;
            }
            break;
        case AllRanges:
            qb.setTables(RANGES);
            qb.setProjectionMap(rangeProjectionMap);
            break;
        case TaskRangesInRange:
            qb.setTables(RANGES);
            qb.setProjectionMap(rangeProjectionMap);
            qb.appendWhere("_task_id = ? AND start >= ? AND (end <= ? OR end IS NULL)");
            whereArgs = new String[3];
            nargs -= 3;
            for (int i=nargs-4; i<nargs; i++) {
                whereArgs[i] = arguments.get(i);
            }
            break;
        case AllRangesInRange:
            qb.setTables(RANGES);
            qb.setProjectionMap(rangeProjectionMap);
            qb.appendWhere("start >= ? AND (end <= ? OR end IS NULL)");
            whereArgs = new String[2];
            whereArgs[0] = arguments.get(nargs-2);
            whereArgs[1] = arguments.get(nargs-1);
        default:
            throw new IllegalArgumentException("Unknown URL " + uri);
        }

        // Run the query and return the results as a Cursor
        SQLiteDatabase mDb = dbHelper.getReadableDatabase();
        Cursor c = qb.query(mDb, projection, null, whereArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match_val = URI_MATCHER.match(uri);
        Uris match = Uris.values()[match_val];
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;
        String taskId, start, end;
        switch (match) {
        case AllTasks:
            count = db.delete(TASK_TABLE, selection, selectionArgs);
            break;
        case TaskId:
            taskId = uri.getPathSegments().get(1);
            count = db.delete(TASK_TABLE, " _id =" + taskId
                    + (!TextUtils.isEmpty(selection) ? 
                            " AND (" + selection + ')' : 
                                ""), 
                    selectionArgs);
            break;
        case RangesForTask:
            taskId = uri.getPathSegments().get(1);
            count = db.delete(RANGES, " _task_id =" + taskId
                    + (!TextUtils.isEmpty(selection) ? 
                            " AND (" + selection + ')' : 
                                ""), 
                    selectionArgs);
            break;
        case AllRanges:
            count = db.delete(RANGES, selection, selectionArgs);
            break;
        case AllRangesInRange:
            start = uri.getPathSegments().get(1);
            end = uri.getPathSegments().get(2);
            count = db.delete(RANGES, " start >= "+start+" AND (end <= "
                    + end+" OR end IS NULL)"
                    + (!TextUtils.isEmpty(selection) ? 
                            " AND (" + selection + ')' : 
                                ""), 
                    selectionArgs);
            break;
        case TaskRangesInRange:
            taskId = uri.getPathSegments().get(1);
            start = uri.getPathSegments().get(2);
            end = uri.getPathSegments().get(3);
            count = db.delete(RANGES, " _task_id = " + taskId +
                    " AND start >= "+start+" AND (end <= "+end+" OR end IS NULL)"
                    + (!TextUtils.isEmpty(selection) ? 
                            " AND (" + selection + ')' : 
                                ""), 
                    selectionArgs);
            break;
        default:
            break;        
        }
        return count;
    }

    @Override
    public String getType(Uri uri) {
        int match_val = URI_MATCHER.match(uri);
        Uris match = Uris.values()[match_val];
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        switch (match) {
        case AllTasks:
            return TASKS_MIME;
        case AllRanges:
            return RANGES_MIME;
        case AllRangesInRange:
            return RANGES_MIME;
        case RangesForTask:
            return RANGES_MIME;
        case TaskId:
            return TASK_MIME;
        case TaskRangesInRange:
            return TASKS_MIME;
            default:
            throw new IllegalArgumentException( "Unknown URI "+uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        ContentValues values;
        // For thread-safety
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        
        int match_val = URI_MATCHER.match(uri);
        Uris match = Uris.values()[match_val];
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Resources r = Resources.getSystem();
        String tableName;
        String nullColumnHack;
        Uri itemUri;
        switch (match) {
        case TaskId:
            if (!values.containsKey(T_NAME)) {
                throw new IllegalArgumentException(r.getString(R.string.task_name_required));
            }
            if (!values.containsKey(T_PRIORITY)) {
                values.put(T_PRIORITY, Task.Priority.Medium.toString());
            }
            tableName = TASK_TABLE;
            nullColumnHack = T_PRIORITY;
            itemUri = TASKS_URI;
            break;
        case RangeForTask:
            if (!values.containsKey(R_START)) {
                throw new IllegalArgumentException(r.getString(R.string.start_time_required));
            }
            tableName = RANGES;
            nullColumnHack = R_END;
            itemUri = RANGES_URI;
            break;
        default:
            throw new IllegalArgumentException("Unknown URI "+uri);            
        }
        long rowId = db.insert(tableName, nullColumnHack, values);
        if (rowId > 0) {
            Uri resultUri = ContentUris.withAppendedId(itemUri, rowId);
            getContext().getContentResolver().notifyChange(resultUri, null);
            return resultUri;
        }
        throw new SQLException( "Failed to insert row into "+uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        int match_val = URI_MATCHER.match(uri);
        Uris match = Uris.values()[match_val];
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        switch (match) {
        case AllTasks:
            break;
        case AllRanges:
            break;
        case AllRangesInRange:
            break;
        case RangesForTask:
            break;
        case TaskId:
            break;
        case TaskRangesInRange:
            break;
        default:
            break;
        }
        return 0;
    }
}
