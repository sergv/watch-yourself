package org.ser.timetracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.ExpandableListActivity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.SimpleExpandableListAdapter;
import android.provider.BaseColumns;

public class Projects extends ExpandableListActivity {
    public static final class Project implements BaseColumns {
        // This class cannot be instantiated
        private Project() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + TimeTracker.AUTHORITY + "/projects");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.ser1.cursor.dir/vnd.ser1.project";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.ser1.cursor.item/vnd.ser1.project";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /**
         * The name of the project
         * <P>Type: TEXT</P>
         */
        public static final String PROJECT_NAME = "name";  

        /**
         * The timestamp on which the project was created
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String CREATED_DATE = "created";
    }
    

}