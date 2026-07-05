package com.mh.librarymanager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Silent update install for the device-owner app. Bypasses [UserManager.DISALLOW_INSTALL_APPS]
 * because the device owner performs the install (not the package installer UI / adb).
 *
 * Maintenance deploy:
 *   adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/library-update.apk
 *   adb shell am broadcast -a com.mh.librarymanager.INSTALL_UPDATE \
 *     -n com.mh.librarymanager/.MaintenanceInstallReceiver
 */
object ApkUpdateInstaller {

    private const val TAG = "ApkUpdateInstaller"

  /** Paths tried in order (adb push targets). */
    val UPDATE_PATHS = listOf(
        "/data/local/tmp/library-update.apk",
        "/sdcard/Download/library-update.apk",
    )

    fun findUpdateApk(): File? =
        UPDATE_PATHS
            .map { File(it) }
            .firstOrNull { it.isFile && it.canRead() }

    /**
     * Copies a picked APK (a `content://` document from a USB stick / SD card)
     * into private cache and installs it as the device owner. Works both for a
     * self-update and for installing another app — the package name is derived
     * from the APK itself, so it is not restricted to our own package.
     *
     * Runs on the caller's (background) thread.
     */
    fun installFromUri(context: Context, uri: Uri): Boolean {
        if (!KioskPolicyManager.isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — cannot install picked APK")
            return false
        }
        val staged = File(context.cacheDir, "picked-install.apk")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staged).use { output -> input.copyTo(output) }
            } ?: run {
                Log.w(TAG, "Could not open picked APK stream")
                return false
            }
            install(context, staged, restrictToSelf = false)
        } catch (e: Exception) {
            Log.e(TAG, "installFromUri failed", e)
            false
        } finally {
            staged.delete()
        }
    }

    fun install(context: Context, apk: File, restrictToSelf: Boolean = true): Boolean {
        if (!KioskPolicyManager.isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — cannot install ${apk.absolutePath}")
            return false
        }
        if (!apk.isFile || !apk.canRead()) {
            Log.w(TAG, "APK not readable: ${apk.absolutePath}")
            return false
        }

        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                // Only pin the package name for the self-update path; a picked
                // APK may legitimately be another app, so let the installer read
                // the package name from the APK in that case.
                if (restrictToSelf) setAppPackageName(context.packageName)
            }

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            FileInputStream(apk).use { input ->
                session.openWrite("base", 0, apk.length()).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    session.fsync(out)
                }
            }

            val callback = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
            session.close()
            Log.i(TAG, "Install session committed for ${apk.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for ${apk.absolutePath}", e)
            false
        }
    }
}
