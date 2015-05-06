package com.plugin.gcm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings.Secure;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

    private static final String TAG = "GCMIntentService";

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    public void onRegistered(Context context, String regId) {
        Log.v(TAG, "onRegistered: " + regId);
        if (PushPlugin.isActive()) {
            try {
                JSONObject json = new JSONObject().put("event", "registered");
                json.put("regid", regId);
                Log.v(TAG, "onRegistered: " + json.toString());
                // Send this JSON data to the JavaScript application above EVENT should be set to the msg type
                // In this case this is the registration ID
                PushPlugin.sendJavascript(json);
            } catch (JSONException e) {
                // No message to the user is sent, JSON failed
                Log.e(TAG, "onRegistered: JSON exception");
            }
        } else {
            try {
                String baseUrl = getBackendUrl(context);
                String packageId = getAccountManagerPackageId(context);
                Log.d(TAG, "Backend baseUrl=" + baseUrl);
                Log.d(TAG, "AccountManager packageId=" + packageId);
                if (baseUrl == null || packageId == null) {
                    Log.d(TAG, "Unable to perform backend login due to missing backend URL");
                    return;
                }

                /* retrieves current username and password */
                Log.d(TAG, "Retrieving login info");
                Context pkgContext = context.getApplicationContext().createPackageContext(packageId, 0);
                SharedPreferences settings = pkgContext.getSharedPreferences("LoginPrefs", 0);
                if (settings == null) {
                    Log.d(TAG, "Unable to perform backend login due to missing login preferences");
                    return;
                }
                String username = settings.getString("__USERNAME__", null);
                String password = settings.getString("__PASSWORD__", null);
                if (username == null || password == null) {
                    Log.d(TAG, "Unable to perform backend login due to missing username or password");
                    return;
                }
                String deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
                BackendLoginRunnable runnable = new BackendLoginRunnable(username, password, baseUrl, regId, deviceId);
                new Thread(runnable).start();
            } catch (Exception e) {
                Log.e(TAG, "An error corred performing silent login", e);
            }
        }
    }

    private static class BackendLoginRunnable implements Runnable {

        private static final int MAX_RETRY = 10;

        private final String username;
        private final String password;
        private final String baseUrl;
        private final String registrationId;
        private final String deviceId;

        BackendLoginRunnable(String username, String password, String baseUrl, String registrationId, String deviceId) {
            super();
            this.username = username;
            this.password = password;
            this.baseUrl = baseUrl;
            this.registrationId = registrationId;
            this.deviceId = deviceId;
        }

        @Override
        public void run() {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
            // Retries many time with increasing time lapse in order to grant backend availability.
            // The backend could be accessible only through the WIFI which can takes several time to be available, respect on the GSM
            // network which is ready on startup and which allows the GCM registration but not the backend access.
            for (int i = 1; i <= MAX_RETRY; i++) {
                try {
                    Log.d(TAG, "Performing backend login for '" + username + "' (retry " + i + ")");
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpParams params = httpClient.getParams();
                    HttpConnectionParams.setConnectionTimeout(params, 4000);
                    HttpConnectionParams.setSoTimeout(params, 4000);
                    HttpPost request = new HttpPost(baseUrl + "/users/login");
                    request.addHeader("Accept", "application/json");
                    request.addHeader("content-type", "application/json");
                    JSONObject requestJson = new JSONObject();
                    requestJson.put("username", username);
                    requestJson.put("password", password);
                    JSONObject device = new JSONObject();
                    device.put("deviceId", deviceId);
                    device.put("devicePlatform", "Android");
                    device.put("notificationDeviceId", registrationId);
                    requestJson.put("device", device);
                    request.setEntity(new StringEntity(requestJson.toString()));
                    HttpResponse response = httpClient.execute(request);
                    if (200 == response.getStatusLine().getStatusCode()) {
                        return;
                    }
                    String msg = "Unable to perform backend login " + response.getStatusLine();
                    if (response.getEntity() != null) {
                        msg += "\n" + EntityUtils.toString(response.getEntity());
                    }
                    Log.e(TAG, msg);
                } catch (Throwable t) {
                    // ignores exceptions
                }
                try {
                    Thread.sleep(i * 2000L);
                } catch (Throwable t2) {
                    // ignores exceptions
                }
            }
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
        return (String) context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.e(TAG, "onError - errorId: " + errorId);
        if (PushPlugin.isActive()) {
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

    private String getBackendUrl(Context context) {
        try {
            JSONObject descr = readFromfile("www/services/_backend.json", context);
            return descr.getString("baseUrl");
        } catch (Exception e) {
            Log.e(TAG, "Unable to retrieve the backend base URL", e);
        }
        return null;
    }

    private String getAccountManagerPackageId(Context context) {
        try {
            JSONObject descr = readFromfile("www/services/_security.json", context);
            return descr.getJSONObject("accountManager").getString("packageName");
        } catch (Exception e) {
            Log.e(TAG, "Unable to retrieve the account-manager package-id", e);
        }
        return null;
    }

    public static JSONObject readFromfile(String fileName, Context context) throws Exception {
        InputStream inputStream = null;
        InputStreamReader streamReader = null;
        BufferedReader reader = null;
        try {
            inputStream = context.getResources().getAssets().open(fileName);
            streamReader = new InputStreamReader(inputStream);
            reader = new BufferedReader(streamReader);
            String line = "";
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            throw new Exception("Unable to read asset resource " + fileName, e);
        } finally {
            if (streamReader != null) {
                try {
                    streamReader.close();
                } catch (Throwable t) {
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable t) {
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable t) {
                }
            }
        }
    }

}
