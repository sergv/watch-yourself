/**
 * 2013 Sergey Vinokurov
 * @author Sergey Vinokurov <serg.foo@gmail.com>
 */
package org.yourself.watch;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import java.util.ArrayList;
import java.util.List;
import static org.yourself.watch.DBHelper.TASK_TABLE;
import static org.yourself.watch.DBHelper.TASK_COLUMNS;
import static org.yourself.watch.DBHelper.RANGES_TABLE;
import static org.yourself.watch.DBHelper.TASK_ID;
import static org.yourself.watch.DBHelper.START;
import static org.yourself.watch.DBHelper.END;
import static org.yourself.watch.DBHelper.NAME;

/**
 *
 * @author ser
 */
public class DBBackup extends AsyncTask<SQLiteDatabase, Integer, Void> {

private ProgressDialog progressDialog;
private Tasks callback;
private boolean cancel = false;
public enum Result { SUCCESS, FAILURE };
public static final int PRIMARY = 0, SECONDARY = 1, SETMAX = 2;
private Result result;
private String message = null;

private SQLiteDatabase source;
private SQLiteDatabase dest;

public DBBackup(Tasks callback, ProgressDialog progress) {
    this.callback = callback;
    progressDialog = progress;
    progressDialog.setProgress(0);
    progressDialog.setSecondaryProgress(0);

    source = null;
    dest = null;
}

private void closeDatabases() {
    if (source != null) {
        source.close();
    }
    if (dest != null) {
        dest.close();
    }
}

@Override
protected Void doInBackground(SQLiteDatabase... ss) {
    source = ss[0];
    dest = ss[1];

    // Read the tasks and IDs
    Cursor readCursor = source.query(TASK_TABLE,
                                     TASK_COLUMNS,
                                     null, null, null, null, "rowid");
    List<Task> tasks = readTasks(readCursor);

    // Match the tasks to tasks in the existing DB, and build re-index list
    readCursor = dest.query(TASK_TABLE,
                            TASK_COLUMNS,
                            null, null, null, null, "rowid");
    List<Task> toReorder = readTasks(readCursor);

    int step = (int)(100.0 / tasks.size());
    publishProgress(SETMAX, tasks.size());

    // For each task in the backup DB, see if there's a matching task in the
    // current DB.  If there is, copy the times for the task over from the
    // backup DB.  If there isn't, copy the task and it's times over.
    for (Task t : tasks) {
        boolean matchedTask = false;
        publishProgress(PRIMARY, step);
        for (Task o : toReorder) {
            if (cancel) return null;
            if (t.getTaskName().equals(o.getTaskName())) {
                copyTimes(source, t.getId(), dest, o.getId());
                toReorder.remove(o);
                matchedTask = true;
                break;
            }
        }
        if (!matchedTask) {
            copyTask(source, t, dest);
        }
    }
    result = Result.SUCCESS;
    message = dest.getPath();
    return null;
}

@Override
protected void onPostExecute(Void v) {
    progressDialog.dismiss();
    closeDatabases();
    callback.finishedCopy(result, message);
}

@Override
protected void onProgressUpdate(Integer... vs) {
    int update_type = vs[0];
    int increment = vs[1];
    switch (update_type) {
    case PRIMARY:
        if (increment == 0) {
            progressDialog.setProgress(0);
        } else {
            progressDialog.incrementProgressBy(increment);
        }
        break;
    case SECONDARY:
        if (increment == 0) {
            progressDialog.setSecondaryProgress(0);
        } else {
            progressDialog.incrementSecondaryProgressBy(increment);
        }
        break;
    case SETMAX:
        progressDialog.setMax(increment);
        break;
    default:
        break;
    }
}

@Override
protected void onCancelled() {
    cancel = true;
    progressDialog.dismiss();
    closeDatabases();
}


private void copyTimes(SQLiteDatabase sourceDb,
                       int sourceId,
                       SQLiteDatabase destDb,
                       int destId) {
    publishProgress(SECONDARY, 0);
    Cursor source = sourceDb.query(RANGES_TABLE,
                                   DBHelper.RANGE_COLUMNS,
                                   DBHelper.TASK_ID + " = ?",
                                   new String[] {String.valueOf(sourceId)},
                                   null, null, null);
    Cursor dest = destDb.query(RANGES_TABLE,
                               DBHelper.RANGE_COLUMNS,
                               DBHelper.TASK_ID + " = ?",
                               new String[] {String.valueOf(destId)},
                               null, null, null);
    List<TimeRange> destTimes = new ArrayList<TimeRange>();
    int step = (int)(100.0 / (dest.getCount() + source.getCount()));
    if (dest.moveToFirst()) {
        do {
            if (cancel) return;
            publishProgress(SECONDARY, step);
            if (!dest.isNull(1)) {
                destTimes.add(new TimeRange(dest.getLong(0), dest.getLong(1)));
            }
        } while (dest.moveToNext());
    }
    dest.close();
    if (source.moveToFirst()) {
        ContentValues values = new ContentValues();
        do {
            if (cancel) return;
            publishProgress(SECONDARY, step);
            final long start = source.getLong(0);
            long end = source.getLong(1);
            if (!source.isNull(1)) {
                TimeRange s = new TimeRange(start, end);
                if (!destTimes.contains(s)) {
                    values.clear();
                    values.put(TASK_ID, destId);
                    values.put(START, start);
                    values.put(END, end);
                    destDb.insert(RANGES_TABLE, null, values);
                }
            }
        } while (source.moveToNext());
    }
    source.close();
}

private void copyTask(SQLiteDatabase sourceDb, Task t, SQLiteDatabase destDb) {
    if (cancel) return;
    ContentValues values = new ContentValues();
    values.put(NAME, t.getTaskName());
    long id = destDb.insert(TASK_TABLE, null, values);
    copyTimes(sourceDb, t.getId(), destDb, (int) id);
}


private List<Task> readTasks(Cursor readCursor) {
    List<Task> tasks = new ArrayList<Task>();
    if (readCursor.moveToFirst()) {
        do {
            int tid = readCursor.getInt(0);
            Task t = new Task(readCursor.getString(1), tid);
            tasks.add(t);
        } while (readCursor.moveToNext());
    }
    readCursor.close();
    return tasks;
}

}
