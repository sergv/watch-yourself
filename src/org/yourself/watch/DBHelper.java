/**
 * TimeTracker
 * ©2008, 2009 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 * 2013 Sergey Vinokurov
 * @author Sergey Vinokurov <serg.foo@gmail.com>
 */
package org.yourself.watch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
// import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {
// private static final String TAG = "DBHelper";

public static final String END = "end";
public static final String START = "start";
public static final String TASK_ID = "task_id";
public static final String[] RANGE_COLUMNS = { START, END };
public static final String NAME = "name";
public static final String[] TASK_COLUMNS = new String[] { "ROWID", NAME };
public static final String TIMETRACKER_DB_NAME = "timetracker.db";
public static final int DBVERSION = 6;
public static final String RANGES_TABLE = "ranges";
public static final String RANGES_TABLE_IDX = "ranges_idx";
public static final String TASK_TABLE = "tasks";
public static final String TASK_TABLE_IDX = "tasks_idx";
public static final String TASK_NAME = "name";
public static final String ID_NAME = "_id";

public DBHelper(Context context) {
    super(context, TIMETRACKER_DB_NAME, null, DBVERSION);
    instance = this;
}

/**
 * Despite the name, this is not a singleton constructor
 */
private static DBHelper instance;
public static DBHelper getInstance() {
    return instance;
}

private static final String CREATE_TASK_TABLE =
    "CREATE TABLE %s ("
    + ID_NAME + " INTEGER PRIMARY KEY AUTOINCREMENT," /* indexed */
    + TASK_NAME + " TEXT COLLATE LOCALIZED NOT NULL"
    + ");";

private static final String CREATE_TASK_TABLE_INDEX =
    "CREATE UNIQUE INDEX " + TASK_TABLE_IDX + " on " + TASK_TABLE +
    " (" + ID_NAME + ") ";

private static final String CREATE_RANGES_TABLE_INDEX =
    "CREATE UNIQUE INDEX " + RANGES_TABLE_IDX + " on " + RANGES_TABLE +
    " (" + TASK_ID + ", " + START + ", " + END + ") ";


@Override
public void onCreate(SQLiteDatabase db) {
    db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
    db.execSQL(CREATE_TASK_TABLE_INDEX);
    db.execSQL("CREATE TABLE " + RANGES_TABLE + "("
               + TASK_ID + " INTEGER NOT NULL," /* indexed */
               + START + " INTEGER NOT NULL," /* indexed */
               + END + " INTEGER"
               + ");");
    db.execSQL(CREATE_RANGES_TABLE_INDEX);
}

@Override
public void onUpgrade(SQLiteDatabase arg0, int oldVersion, int newVersion) {
    if (oldVersion < 4) {
        arg0.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
        arg0.execSQL("insert into temp(rowid," + TASK_NAME + ") select rowid,"
                     + TASK_NAME + " from " + TASK_TABLE + ";");
        arg0.execSQL("drop table " + TASK_TABLE + ";");
        arg0.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
        arg0.execSQL("insert into " + TASK_TABLE + "(" + ID_NAME + "," + TASK_NAME +
                     ") select rowid," + TASK_NAME + " from temp;");
        arg0.execSQL("drop table temp;");
    } else if (oldVersion < 5) {
        arg0.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
        arg0.execSQL("insert into temp(" + ID_NAME + "," + TASK_NAME + ") select rowid,"
                     + TASK_NAME + " from " + TASK_TABLE + ";");
        arg0.execSQL("drop table " + TASK_TABLE + ";");
        arg0.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
        arg0.execSQL("insert into " + TASK_TABLE + "(" + ID_NAME + "," + TASK_NAME +
                     ") select " + ID_NAME + "," + TASK_NAME + " from temp;");
        arg0.execSQL("drop table temp;");
    }

    /* now database is bought up to version 5 */
    if (oldVersion < 6) {
        arg0.execSQL(CREATE_TASK_TABLE_INDEX);
        arg0.execSQL(CREATE_RANGES_TABLE_INDEX);
    }
}

// @Override
// public SQLiteDatabase getReadableDatabase() {
//     Log.d(TAG, "getReadableDatabase");
//     return super.getReadableDatabase();
// }

// @Override
// public SQLiteDatabase getWritableDatabase() {
//     Log.d(TAG, "getWritableDatabase");
//     return super.getWritableDatabase();
// }
}

