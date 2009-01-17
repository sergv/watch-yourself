package net.ser1.timetracker;

import java.io.OutputStream;
import java.io.PrintStream;

import android.database.Cursor;

public class CSVExporter {
    private static String escape( String s ) {
        if (s.contains(",") || s.contains("\"")) {
            s = s.replaceAll("\"", "\"\"");
            s = "\"" + s + "\"";
        }
        return s;
    }
    
    public static void exportRows( OutputStream o, Cursor c ) {
        PrintStream outputStream = new PrintStream(o);
        String prepend = "";
        for (String s : c.getColumnNames()) {
            outputStream.print(prepend);
            outputStream.print(escape(s));
            prepend = ",";
        }
        if (c.moveToFirst()) {
            do {
                outputStream.println();
                prepend = "";
                for (int i=0; i<c.getColumnCount(); i++) {
                    outputStream.print(prepend);
                    outputStream.print(escape(c.getString(i)));
                    prepend = ",";
                }
            } while (c.moveToNext());
        }
        outputStream.print("\n");
    }
}
