package helloworldeng.com.reachability;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    Reachability reachability;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.my_text);

        reachability = new Reachability();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        reachability.stopMonitoringConnection();

        super.onPause();
    }

    @Override
    protected void onResume() {
        final String hostname = "http://www.google.com";
        reachability.startMonitoringConnectionToHost(this, hostname, new Reachability.OnReachabilityChangedListener() {
            @Override
            public void OnReachabilityChanged(boolean newReachability) {
                if(newReachability) {
                    textView.setText("Connected to " + hostname);
                }
                else {
                    textView.setText("Not Connected to " + hostname);
                }
            }
        });

        super.onResume();
    }
}
