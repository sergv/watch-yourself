package org.ser.timetracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class TimeTracker extends Activity {
    public static final String AUTHORITY = "net.ser1.provider.TimeTracker";
    
    /** Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button start_button = (Button)findViewById(R.id.startButton);
        start_button.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                Intent showTasks = new Intent( TimeTracker.this, Tasks.class );
                startActivity( showTasks );
            }
        });
        
    }
}