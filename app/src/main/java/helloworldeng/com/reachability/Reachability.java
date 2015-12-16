package helloworldeng.com.reachability;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Ryan King on 9/18/15.
 * A class for checking whether a particular host is reachable. Is set up to allow using classes to set listeners for a particular host which will be called
 * when connectivity with that host changes.
 */
public class Reachability {
    /**
     * The amount of time to wait until timing out a connection check
     */
    public static int connectionTimeoutTime = 5;

    /**
     * The amount of time in seconds to wait between rechecking host availability.
     */
    public int connectionRecheckTime = 2;

    /**
     * The listener for reachability changes
     */
    public OnReachabilityChangedListener listener;

    /**
     * True if the host is reachable. This is retrieved from several places, so we ahve to make it atomic.
     */
    private AtomicBoolean isReachable;

    /**
     * True if we've checked reachability
     */
    private boolean checkedReachability = false;

    /**
     * Set to false when you want to stop monitoring
     */
    private boolean keepMonitoring = true;

    /**
     * The hostname to monitor, eg. www.google.com
     */
    private String hostName;

    /**
     * The current context
     */
    private Context currentContext;

    /**
     * The timer used for rechecking the connection status
     */
    private Timer connectionRecheckTimer = null;

    /**
     * The custom reachabiltiy check, if any. Used when specific APIs have ways of checking their reachability not covered by default behavior
     */
    private ReachabilityCheck reachabilityCheck = null;

    /**
     * Activity Monitor message receiver
     */
    private BroadcastReceiver receiver = null;

    /**Checks whether a particular host is reachable. Note that this may take some time, so this should be called on a background thread
     * @param context The current application context
     * @param hostName The name of the host you wish to check, ie "https://api.foursquare.com"
     * @return true if the host is reachable, false if otherwise
     */
    public static boolean HostIsReachable(Context context, String hostName) {
        return(HostIsReachable(context,hostName,null));
    }

    /**
     * Checks whether a particular host is reachable. Note that this may take some time, so this should be called on a background thread
     * @param context The current application context
     * @param hostName The name of the host you wish to check, ie "https://api.foursquare.com"
     * @param rCheck The custom reachability check. If null, uses the default behavior.
     * @return true if the host is reachable, false if otherwise
     */
    public static boolean HostIsReachable(Context context,String hostName, ReachabilityCheck rCheck) {
        // First, check we have any sort of connectivity
        boolean isReachable = false;

        if (networkConnected(context)) {
            try {
                if (rCheck != null) {
                    isReachable = rCheck.HostIsReachable(context, hostName);
                } else {
                    URL url = new URL(hostName);
                    HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
                    urlc.setRequestProperty("User-Agent", "Android Application");
                    urlc.setRequestProperty("Connection", "close");
                    urlc.setConnectTimeout(connectionTimeoutTime * 1000);
                    urlc.connect();
                    isReachable = (urlc.getResponseCode() == 200);
                }
            }
            catch(Exception e) {
                Log.d("Connection", e.getLocalizedMessage());
            }
        }

        return isReachable;
    }

    /**
     * Checks whether the device can connect to the network, regardless of whether it's wifi or 3g or what have you
     * @param context The current context
     * @return True if the network is available
     */
    public static boolean networkConnected(Context context) {
        final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo netInfo = connMgr.getActiveNetworkInfo();

        return(netInfo != null && netInfo.isConnected());
    }

    public boolean isReachable() {
        return(isReachable.get());
    }

    private void initialize(Context context, String hostName, ReachabilityCheck check, OnReachabilityChangedListener listener) {
        // set this object's properties
        currentContext = context;
        this.hostName = hostName;
        this.reachabilityCheck = check;
        this.listener = listener;
        this.isReachable = new AtomicBoolean();
        this.isReachable.set(false);
    }

    /**
     * Starts monitoring the connection to a host with a given reachability listener
     * @param context The current context
     * @param hostName The host name to monitor, eg www.google.com
     * @param listener The listener class whose onReachabilityChanged method will be called when the reachability changes
     */
    public void startMonitoringConnectionToHost(Context context, String hostName, OnReachabilityChangedListener listener) {
        // initialize internal variables
        initialize(context,hostName,null,listener);

        // Create the timer to recheck the connection status after receiving either a success or timeout
        connectionRecheckTimer = new Timer();

        new CheckConnectionTask().execute(hostName);
    }

    /**
     * Starts monitoring the connection with a specific reachability check with a given reachability listener
     * @param context The current context
     * @param check The reachability check method. Used for instance when an API has a specific way to check connection to it.
     * @param listener The listener class whose onReachabilityChanged method will be called when the reachability changes
     */
    public void startMonitoringConnectionToHost(Context context, ReachabilityCheck check, OnReachabilityChangedListener listener) {
        // initialize internal vars
        this.initialize(context,null,check,listener);

        // Create the timer to recheck the connection status after receiving either a success or timeout
        connectionRecheckTimer = new Timer();

        new CheckConnectionTask().execute(hostName);

        // Setup broadcast reception
        IntentFilter reachabilityIntentFiler = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean networkConnected = networkConnected(context);
                // only consider this change if the network disconnected, otherwise, we want the normal host check to verify that the app is reachable
                if(networkConnected == false && isReachable.get()) {
                    isReachable.set(false);

                    Reachability.this.listener.OnReachabilityChanged(false);
                }
            }
        };
        context.registerReceiver(receiver, reachabilityIntentFiler);
    }

    public void stopMonitoringConnection() {
        keepMonitoring = false;
        connectionRecheckTimer.cancel();
        connectionRecheckTimer.purge();

        if(receiver != null)
            currentContext.unregisterReceiver(receiver);
    }

    private class CheckConnectionTask extends AsyncTask<String,Void,Boolean>
    {
        @Override
        protected Boolean doInBackground(String... params) {
            // if there's a custom reachability check, use that. Otherwise, use the default behavior
            return HostIsReachable(currentContext,params[0],reachabilityCheck);
        }

        @Override
        protected void onPostExecute(Boolean connected) {
            // If we changed connection status, tell the listener
            if(!checkedReachability || connected != isReachable.get()) {
                isReachable.set(connected);
                checkedReachability = true;
                listener.OnReachabilityChanged(connected);
            }

            // if we should keep monitoring, start monitoring again in connectionRecheckTime in seconds
            if(keepMonitoring) {
                connectionRecheckTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        new CheckConnectionTask().execute(hostName);
                    }
                }, connectionRecheckTime*1000);

            }
        }
    }

    /**
     * This class should be set for any Reachability object to receive notifications on the reachability of the given host
     */
    public static abstract class OnReachabilityChangedListener {
        public abstract void OnReachabilityChanged(boolean newReachability);
    }

    /**
     * This class is used to implement any custom reachability behavior. This is relevant for something like the parse SDK
     */

    public static abstract class ReachabilityCheck {
        public abstract boolean HostIsReachable(Context context,String hostName);
    }
}

