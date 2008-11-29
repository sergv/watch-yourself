/**
 * TimeTracker 
 * ©2008 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    public static final String END = "end";
    public static final String START = "start";
    public static final String TASK_ID = "task_id";
    public static final String[] RANGE_COLUMNS = { START, END };
    public static final String NAME = "name";
    public static final String[] TASK_COLUMNS = new String[] { "ROWID", NAME };
    public static final String TIMETRACKER_DB_NAME = "timetracker.db";
    public static final int DBVERSION = 3;
    public static final String RANGES_TABLE = "ranges";
    public static final String TASK_TABLE = "tasks";
    public static final String TASK_NAME = "name";

    public DBHelper(Context context) {
        super( context, TIMETRACKER_DB_NAME, null, DBVERSION );
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
        + TASK_NAME+" TEXT COLLATE LOCALIZED NOT NULL"
        + ");";
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE ));
        db.execSQL("CREATE TABLE "+RANGES_TABLE+"("
                + TASK_ID+" INTEGER NOT NULL,"
                + START+" INTEGER NOT NULL,"
                + END+" INTEGER"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
        if (arg1 == 2) {
            arg0.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
            arg0.execSQL("insert into temp(rowid,"+TASK_NAME+") select rowid,"
                    +TASK_NAME+" from "+TASK_TABLE+";");
            arg0.execSQL("drop table "+TASK_TABLE+";");
            arg0.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
            arg0.execSQL("insert into "+TASK_TABLE+"(rowid,"+TASK_NAME+") select rowid,"+TASK_NAME+" from temp;");
            arg0.execSQL("drop table temp;");
        }
    }
}
