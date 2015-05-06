package com.plugin.gcm;

import static com.google.android.gcm.GCMConstants.DEFAULT_INTENT_SERVICE_CLASS_NAME;
import android.content.Context;

import com.google.android.gcm.GCMBroadcastReceiver;

/**
 * Implementation of GCMBroadcastReceiver that hard-wires the intent service to be com.plugin.gcm.GCMIntentService, instead of
 * your_package.GCMIntentService.
 */
public class CordovaGCMBroadcastReceiver extends GCMBroadcastReceiver {

    @Override
    protected String getGCMIntentServiceClassName(Context context) {
        return "com.plugin.gcm" + DEFAULT_INTENT_SERVICE_CLASS_NAME;
    }

}