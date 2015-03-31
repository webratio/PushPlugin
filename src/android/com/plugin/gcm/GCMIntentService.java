package com.plugin.gcm;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

    private static final String TAG = "GCMIntentService";

    private static final String PREFERENCES = "com.google.android.gcm";
    private static final String SENDER_IDS_PREF_KEY = "senderIds";

    public synchronized static void registerSenderId(String senderID, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        Set<String> senderIds = prefs.getStringSet("senderIds", new HashSet<String>());
        if (senderIds.add(senderID)) {
            Editor editor = prefs.edit();
            editor.putStringSet(SENDER_IDS_PREF_KEY, senderIds);
            editor.commit();
        }
    }

    public synchronized static void clearSenderIds(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            Editor editor = prefs.edit();
            editor.remove(SENDER_IDS_PREF_KEY);
            editor.commit();
        } catch (Exception e) {
        }
    }

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    protected String[] getSenderIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return prefs.getStringSet("senderIds", new HashSet<String>()).toArray(new String[0]);
    }

    @Override
    public void onRegistered(Context context, String regId) {
        clearSenderIds(context);
        Log.v(TAG, "onRegistered: " + regId);
        try {
            JSONObject json = new JSONObject().put("event", "registered");
            json.put("regid", regId);
            Log.v(TAG, "onRegistered: " + json.toString());

            // Send this JSON data to the JavaScript application above EVENT
            // should be set to the msg type
            // In this case this is the registration ID
            PushPlugin.sendJavascript(json);
        } catch (JSONException e) {
            // No message to the user is sent, JSON failed
            Log.e(TAG, "onRegistered: JSON exception");
        }
    }

    @Override
    public void onUnregistered(Context context, String regId) {
        Log.d(TAG, "onUnregistered - regId: " + regId);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        Log.d(TAG, "onMessage - context: " + context);

        // Extract the payload from the message
        Bundle extras = intent.getExtras();
        if (extras != null) {
            // if we are in the foreground, just surface the payload, else post
            // it to the statusbar
            if (PushPlugin.isInForeground()) {
                extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
            } else {
                extras.putBoolean("foreground", false);

                // Send a notification if there is a message
                if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                    createNotification(context, extras);
                }
            }
        }
    }

    public void createNotification(Context context, Bundle extras) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String appName = getAppName(this);

        Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("pushBundle", extras);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int defaults = Notification.DEFAULT_ALL;

        if (extras.getString("defaults") != null) {
            try {
                defaults = Integer.parseInt(extras.getString("defaults"));
            } catch (NumberFormatException e) {
            }
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context).setDefaults(defaults)
                .setSmallIcon(context.getApplicationInfo().icon).setWhen(System.currentTimeMillis())
                .setContentTitle(extras.getString("title")).setTicker(extras.getString("title")).setContentIntent(contentIntent)
                .setAutoCancel(true);

        String message = extras.getString("message");
        if (message != null) {
            mBuilder.setContentText(message);
        } else {
            mBuilder.setContentText("<missing message content>");
        }

        String msgcnt = extras.getString("msgcnt");
        if (msgcnt != null) {
            mBuilder.setNumber(Integer.parseInt(msgcnt));
        }

        int notId = 0;

        try {
            notId = Integer.parseInt(extras.getString("notId"));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
        }

        mNotificationManager.notify((String) appName, notId, mBuilder.build());
    }

    private static String getAppName(Context context) {
        CharSequence appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());

        return (String) appName;
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.e(TAG, "onError - errorId: " + errorId);
        try {
            JSONObject json = new JSONObject().put("event", "error");
            json.put("msg", errorId);
            PushPlugin.sendJavascript(json);
        } catch (Exception e) {
            // No message to the user is sent, JSON failed
            Log.e(TAG, "error callback exception", e);
        }
    }

}
