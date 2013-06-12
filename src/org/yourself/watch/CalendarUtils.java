/**
 * WatchYourself timetracker
 * 2013 Sergey Vinokurov
 * @author Sergey Vinokurov <serg.foo@gmail.com>
 */
package org.yourself.watch;

import java.util.Calendar;

class CalendarUtils {
/** fields that are relevant within a single day */
public static final int[] CALENDAR_DAY_FIELDS = {Calendar.HOUR_OF_DAY,
                                                 Calendar.MINUTE,
                                                 Calendar.SECOND,
                                                 Calendar.MILLISECOND
                                                };

public static Calendar resetDayFields(Calendar c) {
    for (int field : CALENDAR_DAY_FIELDS) {
        c.set(field, c.getMinimum(field));
    }
    return c;
}

}

