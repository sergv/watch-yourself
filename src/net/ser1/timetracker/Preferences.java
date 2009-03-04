/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.ser1.timetracker;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ser
 */
public class Preferences extends ListActivity implements OnClickListener {
    public static final int LARGE = 24;
    public static final int MEDIUM = 16;
    private static final String BOOL = "bool";
    private static final String CURRENT = "current";
    private static final String CURRENTVALUE = "current-value";
    private static final String DISABLED = "disabled";
    private static final String DISABLEDVALUE = "disabled-value";
    private static final String INT = "int";
    private static final String PREFERENCE = "preference";
    private static final String PREFERENCENAME = "preference-name";
    private static final String VALUETYPE = "value-type";
    private SharedPreferences applicationPreferences;
    private List<Map<String,String>> prefs;
    private SimpleAdapter adapter;
    protected final String PREFS_ACTION = "PrefsAction";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applicationPreferences = getSharedPreferences(Tasks.TIMETRACKERPREF, MODE_PRIVATE);
        prefs = new ArrayList<Map<String,String>>();
        setContentView(R.layout.preferences);

        Map<String,String> pref = new HashMap<String,String>();
        pref.put(PREFERENCE, getString(R.string.week_start_day));
        final int weekStart = applicationPreferences.getInt(Tasks.START_DAY, 0) % 2;
        final String sunday = getString(R.string.sunday);
        final String monday = getString(R.string.monday);
        pref.put(CURRENT, weekStart == 0 ? sunday : monday);
        pref.put(DISABLED, weekStart == 0 ? monday : sunday);
        pref.put(CURRENTVALUE,String.valueOf(weekStart == 0 ? 0 : 1 ));
        pref.put(DISABLEDVALUE,String.valueOf(weekStart == 0 ? 1 : 0 ));
        pref.put(VALUETYPE,INT);
        pref.put(PREFERENCENAME,Tasks.START_DAY);
        prefs.add(pref);

        pref = new HashMap<String,String>();
        pref.put(PREFERENCE, getString(R.string.hour_mode));
        final boolean currentMode = applicationPreferences.getBoolean(Tasks.MILITARY, true);
        final String civilian = "1:00 pm";
        final String military = "13:00";
        pref.put(CURRENT, currentMode ? military : civilian);
        pref.put(DISABLED, currentMode ? civilian : military);
        pref.put(CURRENTVALUE, String.valueOf(currentMode));
        pref.put(DISABLEDVALUE,String.valueOf(!currentMode));
        pref.put(VALUETYPE,BOOL);
        pref.put(PREFERENCENAME,Tasks.MILITARY);
        prefs.add(pref);

        pref = new HashMap<String,String>();
        pref.put(PREFERENCE, getString(R.string.concurrency));
        final boolean concurrency = applicationPreferences.getBoolean(Tasks.CONCURRENT, false);
        final String concurrent = getString(R.string.concurrent);
        final String exclusive = getString(R.string.exclusive);
        pref.put(CURRENT, concurrency ? concurrent : exclusive);
        pref.put(DISABLED, concurrency ? exclusive : concurrent);
        pref.put(CURRENTVALUE,String.valueOf(concurrency));
        pref.put(DISABLEDVALUE,String.valueOf(!concurrency));
        pref.put(VALUETYPE,BOOL);
        pref.put(PREFERENCENAME,Tasks.CONCURRENT);
        prefs.add(pref);

        pref = new HashMap<String,String>();
        pref.put(PREFERENCE, getString(R.string.sound));
        final boolean sound = applicationPreferences.getBoolean(Tasks.SOUND, false);
        final String soundEnabled = getString(R.string.sound_enabled);
        final String soundDisabled = getString(R.string.sound_disabled);
        pref.put(CURRENT,sound ? soundEnabled : soundDisabled);
        pref.put(DISABLED, sound ? soundDisabled : soundEnabled);
        pref.put(CURRENTVALUE,String.valueOf(sound));
        pref.put(DISABLEDVALUE,String.valueOf(!sound));
        pref.put(VALUETYPE,BOOL);
        pref.put(PREFERENCENAME,Tasks.SOUND);
        prefs.add(pref);

        pref = new HashMap<String,String>();
        pref.put(PREFERENCE,getString(R.string.font_size));
        final int fontSize = applicationPreferences.getInt(Tasks.FONTSIZE,MEDIUM);
        final String mediumFont = getString(R.string.medium_font);
        final String largeFont = getString(R.string.large_font);
        pref.put(CURRENT,fontSize==MEDIUM ? mediumFont : largeFont);
        pref.put(DISABLED,fontSize==MEDIUM ? largeFont : mediumFont);
        pref.put(CURRENTVALUE,String.valueOf(fontSize));
        pref.put(DISABLEDVALUE,String.valueOf(fontSize == MEDIUM ? LARGE : MEDIUM));
        pref.put(VALUETYPE,INT);
        pref.put(PREFERENCENAME,Tasks.FONTSIZE);
        prefs.add(pref);

        adapter = new SimpleAdapter(this,
                prefs,
                R.layout.preferences_row,
                new String[] {PREFERENCE,CURRENT},
                new int[] {R.id.preference_name, R.id.current_value} );
        
        setListAdapter(adapter);
        findViewById(R.id.pref_accept).setOnClickListener(this);
        
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Map<String,String> pref = prefs.get((int)id);

        String current = pref.get(CURRENT);
        String disabled = pref.get(DISABLED);
        pref.put( CURRENT,disabled);
        pref.put( DISABLED,current);
        String current_value = pref.get(CURRENTVALUE);
        String disabled_value = pref.get(DISABLEDVALUE);
        pref.put(CURRENTVALUE,disabled_value);
        pref.put(DISABLEDVALUE,current_value);

        adapter.notifyDataSetChanged();
        this.getListView().invalidate();
    }

    public void onClick(View v) {
        Intent returnIntent = getIntent();
        SharedPreferences.Editor ed = applicationPreferences.edit();
        for (Map<String,String> pref : prefs) {
            String prefName = pref.get(PREFERENCENAME);
            if (pref.get(VALUETYPE).equals(INT)) {
                final Integer value = Integer.valueOf(pref.get(CURRENTVALUE));
                if (value != applicationPreferences.getInt(prefName,0)) {
                    ed.putInt(prefName,value);
                    returnIntent.putExtra(prefName, true);
                }
            } else if (pref.get(VALUETYPE).equals(BOOL)) {
                final Boolean value = Boolean.valueOf(pref.get(CURRENTVALUE));
                if (value != applicationPreferences.getBoolean(prefName,false)) {
                    ed.putBoolean(prefName,value);
                    returnIntent.putExtra(prefName, true);
                }
            }
        }
        ed.commit();

        getIntent().putExtra(PREFS_ACTION, PREFS_ACTION);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
