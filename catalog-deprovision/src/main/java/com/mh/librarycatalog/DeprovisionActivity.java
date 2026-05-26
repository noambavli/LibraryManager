package com.mh.librarycatalog;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class DeprovisionActivity extends Activity {

    private static final String TAG = "DeprovisionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.isDeviceOwnerApp(getPackageName())) {
            dpm.clearDeviceOwnerApp(getPackageName());
            Log.i(TAG, "clearDeviceOwnerApp invoked");
        }
        finish();
    }
}
