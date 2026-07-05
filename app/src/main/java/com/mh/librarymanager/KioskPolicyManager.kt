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

    /**
     * System document-picker packages allowed to run inside lock task so the
     * "choose a file" screen can appear for imports and the in-app APK update.
     * Both the AOSP and Google package names are whitelisted; only the one that
     * exists on a given device is used.
     */
    private val DOCUMENT_PICKER_PACKAGES = listOf(
        "com.android.documentsui",
        "com.google.android.documentsui",
    )

    private fun lockTaskPackages(packageName: String): Array<String> =
        (listOf(packageName) + DOCUMENT_PICKER_PACKAGES).toTypedArray()

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, KioskDeviceAdminReceiver::class.java)

    fun devicePolicyManager(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        devicePolicyManager(context).isDeviceOwnerApp(context.packageName)

    fun isAdminActive(context: Context): Boolean =
        devicePolicyManager(context).isAdminActive(adminComponent(context))

    fun isKioskReady(context: Context): Boolean = isDeviceOwner(context)

    /** Briefly lift kiosk so a system USB-debugging dialog can appear. */
    fun suspendKioskForMaintenance(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = devicePolicyManager(context)
        val admin = adminComponent(context)
        try {
            dpm.setLockTaskPackages(admin, arrayOf())
            dpm.setStatusBarDisabled(admin, false)
            Log.i(TAG, "Kiosk suspended for PC authorize maintenance")
        } catch (e: Exception) {
            Log.w(TAG, "Could not suspend kiosk for maintenance", e)
        }
    }

    fun applyPolicies(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — kiosk policies skipped")
            return
        }

        val dpm = devicePolicyManager(context)
        val admin = adminComponent(context)
        val packageName = context.packageName

        try {
            // Whitelist our app plus the system file picker so imports and the
            // in-app APK update can open their picker while the kiosk is locked.
            dpm.setLockTaskPackages(admin, lockTaskPackages(packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            dpm.setKeyguardDisabled(admin, true)
            dpm.setStatusBarDisabled(admin, true)

            setHomeLauncher(context, dpm, admin)

            // Sealed device. Nobody — not a user, not the Play Store, not a
            // sideload, not a PC over adb — may install or remove apps, and adb
            // itself is turned off. The one exception is the device-owner app
            // itself: it can still install/update packages in-process (that path
            // bypasses DISALLOW_INSTALL_APPS), which is how staff update the app
            // or add apps from a USB stick via the password-protected screen.
            dpm.setUninstallBlocked(admin, packageName, true)

            val restrictions = buildList {
                add(UserManager.DISALLOW_INSTALL_APPS)
                add(UserManager.DISALLOW_UNINSTALL_APPS)
                add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                add(UserManager.DISALLOW_DEBUGGING_FEATURES)
                add(UserManager.DISALLOW_SAFE_BOOT)
                add(UserManager.DISALLOW_FACTORY_RESET)
                add(UserManager.DISALLOW_ADD_USER)
                add(UserManager.DISALLOW_CONFIG_WIFI)
                add(UserManager.DISALLOW_CONFIG_BLUETOOTH)
                add(UserManager.DISALLOW_ADJUST_VOLUME)
                add(UserManager.DISALLOW_CREATE_WINDOWS)
                add(UserManager.DISALLOW_FUN)
                add(UserManager.DISALLOW_OUTGOING_CALLS)
                add(UserManager.DISALLOW_SMS)
            }
            for (restriction in restrictions) {
                dpm.addUserRestriction(admin, restriction)
            }

            // A USB flash drive / SD card must still mount so staff can import
            // books and pick an update APK directly on the tablet.
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setPermissionPolicy(
                    admin,
                    DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
                )
            }

            UsbMaintenance.applyUsbDefaults(context)

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
