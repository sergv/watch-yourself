package org.ser.timetracker;

import java.util.HashMap;

import org.ser.timetracker.Projects.Project;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class ProjectsProvider extends ContentProvider {
    
    private static final String DBNAME = "timetracker.db";
    private static final int DBVERSION = 2;
    private static final String PROJECTS_TABLE_NAME = "projects";
    private static final UriMatcher uriMatcher;
    private static HashMap<String, String> projectionMap;
    
    private static enum Uris {
        AllProjects, ProjectId
    }
    
    private DBMaintenanceHelper dbHelper = null;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean onCreate() {
        dbHelper = new DBMaintenanceHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        int match = uriMatcher.match(uri);
        if (match == Uris.AllProjects.ordinal()) {
            qb.setTables(PROJECTS_TABLE_NAME);
            qb.setProjectionMap(projectionMap);
        } else if (match == Uris.ProjectId.ordinal()) {
            qb.setTables(PROJECTS_TABLE_NAME);
            qb.setProjectionMap(projectionMap);
            qb.appendWhere(Project._ID + "=" + uri.getPathSegments().get(1));
        } else {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Get the database and run the query
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, "modified NAME");

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;
        int match = uriMatcher.match(uri);
        if (match == Uris.AllProjects.ordinal()) {
            count = db.update(PROJECTS_TABLE_NAME, values, selection, selectionArgs);
        } else if (match == Uris.ProjectId.ordinal()) {
            String projectId = uri.getPathSegments().get(1);
            count = db.update(PROJECTS_TABLE_NAME, values, Project._ID + "=" 
                    + projectId 
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), 
                    selectionArgs);
        } else {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;    
    }

    
    /**
     * A class to assist in creating and upgrading the projects DB
     * 
     * @author ser
     *
     */
    private static class DBMaintenanceHelper extends SQLiteOpenHelper {

        public DBMaintenanceHelper(Context context) {
            super(context, DBNAME, null, DBVERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + PROJECTS_TABLE_NAME + " ("
                    + Project._ID + " INTEGER PRIMARY KEY,"
                    + Project.PROJECT_NAME + " TEXT,"
                    + Project.CREATED_DATE + " INTEGER,"
                    + ");");        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            /* Don't need to do this; no prior version.
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS notes");
            onCreate(db);
            */
        }   
    }
    
    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(TimeTracker.AUTHORITY, "projects", Uris.AllProjects.ordinal());
        uriMatcher.addURI(TimeTracker.AUTHORITY, "projects/#", Uris.ProjectId.ordinal());

        projectionMap = new HashMap<String, String>();
        projectionMap.put(Project._ID, Project._ID);
        projectionMap.put(Project.PROJECT_NAME, Project.PROJECT_NAME);
        projectionMap.put(Project.CREATED_DATE, Project.CREATED_DATE);
    }

}
