package com.mh.librarymanager

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Keeps USB ready for PC maintenance: debugging on and default mode file
 * transfer where the device owner is allowed to set it.
 *
 * Called on boot and before adb-triggered imports so staff never have to
 * pick "File transfer" on the tablet.
 */
object UsbMaintenance {

    private const val TAG = "UsbMaintenance"

    /**
     * Public key for the LibraryTool PC bundle (desktop/adb_bundle/.android/adbkey.pub).
     * Best-effort: whitelists that PC identity so Windows connects without a prompt.
     */
    private const val LIBRARY_TOOL_ADB_PUB =
        "QAAAAI+Xw4ORWAJd3DlAaiie2HJXF8qFJbQBpm3PbnDXdbw7JZ36/WrrMFirprPPXLBV1fsaUMiv6eIJyOsdTZkdApMsVaRECw4IV7vabg83XuUYsQxiTs2RSiackuKO876+y2r1KBftdsLP/IsB79GFdAl8MtW7UyrulutYCKoBgXhd0Pw0VHKTF9afANL4ptPWqEFKzYTifRSto9JXQkoxbN6WSnSX2KmQbeT6nnDKFqeQGOPh5lBP9md0rkZzetaiPrAL4bHRN+BI8WfOOhE1vgxgU9pKz9h7uq9Fd4ZmaO2R8CuWmObYGCYXCvXdUcgcwgL1ReoAz71/5bssACFcIw767sC/4EL+H0AEBfLTycPtbxqZ/qcVoofOI6kRQz12pHB+2O9qRPMRSGczMionOsHRq0usXa7r6WjZjz1+J+q5fLtmudGgHC3r0W1T8s/wsGK1rGj1nW5SrnkcxKhIIEs2/tpIkguKQPvMnZtlY2C9peXkEgJSN9X9EcxY/vcEYgaVdbjyLOcq+7N8rgcP/OU1fV9R5wO1+Bae/77yNp/CXNTy/DVuZBWlWLlcrisdrLtoixXyTAWRHMgIxJVTeJPBBTflgJNsMCi5IuBnZMYnjNFFvLFMnbsntj/chOTZfVucy/s+T7LGarYqVBRcP6JPKVqiYzmEm1OQypTMKV2z/elFsQEAAQA= noam@Mac"

    fun applyUsbDefaults(context: Context) {
        if (!KioskPolicyManager.isDeviceOwner(context)) return
        try {
            // Keep USB debugging available for the PC tool (adb push + broadcast).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    1,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable adb", e)
        }

        // Best-effort: default new USB connections to MTP (file transfer).
        // Exact key/value varies by OEM; shell fallback helps on Samsung etc.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putString(
                    context.contentResolver,
                    "usb_default_functions",
                    "mtp",
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set usb_default_functions", e)
        }

        try {
            Runtime.getRuntime().exec(arrayOf("cmd", "usb", "setFunctions", "mtp"))
        } catch (e: Exception) {
            Log.w(TAG, "cmd usb setFunctions mtp failed", e)
        }

        authorizeLibraryToolAdbKey()
    }

    private fun authorizeLibraryToolAdbKey() {
        try {
            Runtime.getRuntime().exec(
                arrayOf(
                    "sh", "-c",
                    "grep -qF '${LIBRARY_TOOL_ADB_PUB.substringBefore(' ')}' " +
                        "/data/misc/adb/adb_keys 2>/dev/null || " +
                        "echo '$LIBRARY_TOOL_ADB_PUB' >> /data/misc/adb/adb_keys",
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not add LibraryTool adb key", e)
        }
    }
}
