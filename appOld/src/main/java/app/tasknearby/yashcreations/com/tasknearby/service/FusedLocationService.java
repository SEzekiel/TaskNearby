package app.tasknearby.yashcreations.com.tasknearby.service;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

import app.tasknearby.yashcreations.com.tasknearby.AlarmActivity;
import app.tasknearby.yashcreations.com.tasknearby.Constants;
import app.tasknearby.yashcreations.com.tasknearby.R;
import app.tasknearby.yashcreations.com.tasknearby.TaskDetailActivity;
import app.tasknearby.yashcreations.com.tasknearby.Utility;
import app.tasknearby.yashcreations.com.tasknearby.database.TasksContract;

/**
 * DEPENDENCY  - ActivityDetectionService
 * <p>
 * Created by Yash on 28/05/15.
 * <p>
 * FusedLocationService : A service that keeps running in the background to check for upcoming
 * reminders.
 * As soon as the location of the user changes the @method onLocationChanged is called with the
 * new location
 * of the user. This service automatically uses ActivityRecognition API to turn location updates
 * on and off.
 */

public class FusedLocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ResultCallback<Status> {

    public final String TAG = "FusedLocationService";

    public static boolean isAlarmRunning = false;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private ActivityDetectionReceiver mReceiver;

    Utility utility = new Utility();
    int ACCURACY = LocationRequest.PRIORITY_HIGH_ACCURACY;
    boolean mReceivingLocationUpdates = false;
    Cursor cursor;

    @Override
    public void onCreate() {
        Log.i(TAG, "FusedLocationService onCreate!");

        if (utility.checkPlayServices(this)) {
            buildGoogleApiClient();

            // this receiver just receives activity detection data and updates the interval.
            mReceiver = new ActivityDetectionReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter
                    (Constants.ACTIVITY_DETECTION_INTENT_FILTER));

            // noting the accuracy settings.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pref_string = prefs.getString(getString(R.string.pref_accuracy_key), getString
                    (R.string.pref_accuracy_default));
            if (pref_string.equals(getString(R.string.pref_accuracy_balanced)))
                ACCURACY = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;

            // a location request, that'll be issued to Google play services.
            mLocationRequest = new LocationRequest();
            createLocationRequest(Constants.UPDATE_INTERVAL);
        }
    }

    // called when service starts.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mGoogleApiClient != null)
            mGoogleApiClient.connect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            stopActivityUpdates();
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();
    }

    /**
     * Google API Client callbacks. Called when connected to Google Play Services (hereafter GMS).
     */
    @Override
    public void onConnected(Bundle arg0) {
        startLocationUpdates();
        startActivityUpdates();
    }

    protected void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                    mLocationRequest, this);
        mReceivingLocationUpdates = true;
    }


    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mReceivingLocationUpdates = false;
    }

    protected void createLocationRequest(long updateInterval) {
        mLocationRequest.setInterval(updateInterval);
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);
        mLocationRequest.setPriority(ACCURACY);
        mLocationRequest.setSmallestDisplacement(Constants.SMALLEST_DISPLACEMENT);
    }

    /****************
     * onLocationChanged Method : Called every time user's location changes
     *
     * @param loc : Contains the new location
     *************/
    @Override
    public void onLocationChanged(Location loc) {

        cursor = this.getContentResolver().query(TasksContract.TaskEntry.CONTENT_URI,
                Constants.PROJECTION_TASKS, null, null, null);

        int remindDistance, placeDistance;
        boolean isMarkedDone;
        String placeName;

        while (cursor != null && cursor.moveToNext()) {
            placeName = cursor.getString(Constants.COL_LOCATION_NAME);
            remindDistance = cursor.getInt(Constants.COL_REMIND_DIS);
            isMarkedDone = cursor.getString(Constants.COL_DONE).equals("true");
            placeDistance = utility.getDistanceByPlaceName(placeName, loc, this);

            updateDatabaseDistance(placeDistance);

            if (isAlarmRunning)
                Log.i(TAG, "ALARM already running=============>");

            if ((placeDistance <= remindDistance)
                    && (placeDistance != 0)
                    && !isMarkedDone
                    && !isAlarmRunning
                    && !isSnoozed()) {
                showNotification();
                if (cursor.getString(Constants.COL_ALARM).equals("true")) {
                    Log.i(TAG, "Starting Alarm Activity.");

                    Intent intent2 = new Intent(this, AlarmActivity.class);
                    intent2.putExtra(Constants.TaskID, cursor.getString(Constants.COL_TASK_ID));
                    intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent2);
                }
            }
        }
        cursor.close();
    }


    /**
     * Fore every place that we process, a distance from current location is kept in the database.
     * WHen onLocationChanged is called, the distances need to be updated in the database.
     */
    private void updateDatabaseDistance(int placeDistance) {

        ContentValues taskValues = new ContentValues();
        taskValues.put(TasksContract.TaskEntry.COLUMN_TASK_NAME, cursor.getString(Constants
                .COL_TASK_NAME));
        taskValues.put(TasksContract.TaskEntry.COLUMN_LOCATION_NAME, cursor.getString(Constants
                .COL_LOCATION_NAME));
        taskValues.put(TasksContract.TaskEntry.COLUMN_LOCATION_COLOR, cursor.getInt(Constants
                .COL_TASK_COLOR));
        taskValues.put(TasksContract.TaskEntry.COLUMN_LOCATION_ALARM, cursor.getString(Constants
                .COL_ALARM));
        taskValues.put(TasksContract.TaskEntry.COLUMN_MIN_DISTANCE, placeDistance);
        taskValues.put(TasksContract.TaskEntry.COLUMN_DONE_STATUS, cursor.getString(Constants
                .COL_DONE));

        this.getContentResolver().update(
                TasksContract.TaskEntry.CONTENT_URI,
                taskValues, TasksContract.TaskEntry._ID + "=?",
                new String[]{cursor.getString(Constants.COL_TASK_ID)}
        );
    }

    private void showNotification() {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService
                (NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, TaskDetailActivity.class);
        intent.putExtra(Constants.TaskID, cursor.getString(Constants.COL_TASK_ID));
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent
                .FLAG_UPDATE_CURRENT);

        //TODO: Remove
        Intent markDoneIntent = new Intent(this, NotificationClickHandler.class);
        markDoneIntent.putExtra(Constants.TaskID, cursor.getString(Constants.COL_TASK_ID))
                .putExtra(Constants.NOTIFICATION_BUTTON_ACTION, 1);
        PendingIntent markAsDonePI = PendingIntent.getService(this, 1, markDoneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);


        Intent snoozeIntent = new Intent(this, NotificationClickHandler.class);
        snoozeIntent.putExtra(Constants.TaskID, cursor.getString(Constants.COL_TASK_ID))
                .putExtra(Constants.NOTIFICATION_BUTTON_ACTION, 2);
        PendingIntent snoozePI = PendingIntent.getService(this, 3, snoozeIntent, PendingIntent
                .FLAG_UPDATE_CURRENT);


        NotificationCompat.Action markDoneAction = new NotificationCompat.Action.Builder(R
                .drawable.ic_location,
                "Mark Done", markAsDonePI).build();
        NotificationCompat.Action snoozeAction = new NotificationCompat.Action.Builder(R.drawable
                .ic_update_grey_500_36dp,
                "Snooze", snoozePI).build();


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(cursor.getString(Constants.COL_TASK_NAME))
                .setContentText(cursor.getString(Constants.COL_LOCATION_NAME))
                .setSmallIcon(getNotificationIcon())
                .setContentIntent(pIntent)
                .setAutoCancel(false)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[]{0, 500, 100, 500})
                .addAction(markDoneAction)
                .addAction(snoozeAction);

        if (Build.VERSION.SDK_INT < 16)
            notificationManager.notify(0, notificationBuilder.getNotification());
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                notificationBuilder.setColor(ContextCompat.getColor(this, R.color.teal));
            notificationManager.notify(0, notificationBuilder.build());
        }
    }

    private int getNotificationIcon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return R.drawable.ic_stat_tasknearby_notif_icon;
        return R.mipmap.ic_launcher;
    }

    public boolean isSnoozed() {
        Log.e(TAG, "isSnoozed: true");
        if (System.currentTimeMillis() < cursor.getLong(Constants.COL_SNOOZE) + Constants
                .SNOOZE_TIME_DURATION)
            return true;
        Log.e(TAG, "isSnoozed: false THE ALARM ISN'T SNOOZED");
        return false;
    }


    /**
     * ACTIVITY RECOGNITION LOGIC
     *
     *
     */

    protected void startActivityUpdates() {
        if (!mGoogleApiClient.isConnected())
            return;
        ActivityRecognition.ActivityRecognitionApi
                .requestActivityUpdates(mGoogleApiClient, Constants.ActDetectionInterval_ms,
                        getPendingIntent())
                .setResultCallback(this);
    }

    protected PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, ActivityDetectionService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    protected void stopActivityUpdates() {
        if (!mGoogleApiClient.isConnected())
            return;
        ActivityRecognition.ActivityRecognitionApi
                .removeActivityUpdates(mGoogleApiClient, getPendingIntent())
                .setResultCallback(this);
    }

    /**
     * Contains the status of ActivityRecognition service (from GMS (google play services)).
     */
    @Override
    public void onResult(Status status) {   /*ActivityDetectionResult handler*/
        if (status.isSuccess())
            Log.e(TAG, "Activity Detection Initiated Successfully");
        else
            Log.e(TAG, "Activity Detection Failed!");
    }

    /**
     * {@link ActivityDetectionService} sends the broadcast, which is caught by this receiver.
     * Activity Detection Logic
     */
    public class ActivityDetectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "ActivityDetection BroadCast Received");
            ArrayList<DetectedActivity> detectedActivities =
                    intent.getParcelableArrayListExtra(Constants.ReceiverIntentExtra);

            int confidence;
            for (DetectedActivity i : detectedActivities) {
                confidence = i.getConfidence();
                switch (i.getType()) {
                    case DetectedActivity.STILL:
                        if (confidence > 50 && mReceivingLocationUpdates) {
                            stopLocationUpdates();
                            Log.e(TAG, "Still,hence Stopping Location Updates!");
                        }
                        break;

                    case DetectedActivity.IN_VEHICLE:
                        if (confidence > 50)
                            restartLocationUpdates(5000);
                        break;

                    case DetectedActivity.ON_BICYCLE:
                    case DetectedActivity.RUNNING:
                        if (confidence > 60)
                            restartLocationUpdates(5000);
                        else if (confidence > 50)
                            restartLocationUpdates(10000);
                        break;

                    case DetectedActivity.WALKING:
                    case DetectedActivity.ON_FOOT:
                        if (confidence > 50)
                            restartLocationUpdates(15000);
                        break;

                    case DetectedActivity.UNKNOWN:
                        if (confidence > 60)
                            restartLocationUpdates(10000);
                        break;
                }
            }
        }


        void restartLocationUpdates(long newInterval) {
            if ((mLocationRequest != null && mLocationRequest.getInterval() != newInterval)
                    || !mReceivingLocationUpdates) {
                Log.i(TAG, "Restarting with UPDATE_INTERVAL = " + newInterval);
                createLocationRequest(newInterval);
                if (mReceivingLocationUpdates)
                    stopLocationUpdates();
                startLocationUpdates();
            } else
                Log.i(TAG, "Update Interval Is same as before.So, not restarting!");
        }

    }

    /**
     * Ignore
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result
                .getErrorCode());
    }
    /**
     * Ignore
     */
    @Override
    public void onConnectionSuspended(int arg0) {
        mGoogleApiClient.connect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
