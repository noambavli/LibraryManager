package com.mh.librarymanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log

object KioskPolicyManager {

    private const val TAG = "KioskPolicyManager"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, KioskDeviceAdminReceiver::class.java)

    fun devicePolicyManager(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        devicePolicyManager(context).isDeviceOwnerApp(context.packageName)

    fun isAdminActive(context: Context): Boolean =
        devicePolicyManager(context).isAdminActive(adminComponent(context))

    fun isKioskReady(context: Context): Boolean = isDeviceOwner(context)

    fun applyPolicies(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — kiosk policies skipped")
            return
        }

        val dpm = devicePolicyManager(context)
        val admin = adminComponent(context)
        val packageName = context.packageName

        try {
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            dpm.setKeyguardDisabled(admin, true)
            dpm.setStatusBarDisabled(admin, true)

            setHomeLauncher(context, dpm, admin)

            val restrictions = arrayOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_ADJUST_VOLUME,
                UserManager.DISALLOW_CREATE_WINDOWS,
                UserManager.DISALLOW_FUN,
                UserManager.DISALLOW_OUTGOING_CALLS,
                UserManager.DISALLOW_SMS,
            )
            for (restriction in restrictions) {
                dpm.addUserRestriction(admin, restriction)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setPermissionPolicy(
                    admin,
                    DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
                )
            }

            Log.i(TAG, "Kiosk policies applied")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply kiosk policies", e)
        }
    }

    private fun setHomeLauncher(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
    ) {
        val homeIntentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val homeActivity = ComponentName(context, MainActivity::class.java)
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.addPersistentPreferredActivity(admin, homeIntentFilter, homeActivity)
    }
}
