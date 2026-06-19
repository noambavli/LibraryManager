package com.mh.librarymanager.data.homemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Validates, optionally downscales, and encodes overview map uploads.
 * Returns Hebrew error messages suitable for the Windows Tool UI.
 */
object HomeOverviewMapProcessor {

    private const val MAX_INPUT_BYTES = 20L * 1024L * 1024L
    private const val MAX_LONG_EDGE = 2400
    private const val MIN_LONG_EDGE = 320
    private const val PNG_QUALITY = 100

    data class Preview(
        val pngBytes: ByteArray,
        val width: Int,
        val height: Int,
        val originalWidth: Int,
        val originalHeight: Int,
        val wasResized: Boolean,
    )

    sealed interface Result {
        data class Ok(val preview: Preview) : Result
        data class Error(val message: String) : Result
    }

    fun process(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        val size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
        if (size > MAX_INPUT_BYTES) {
            return Result.Error("הקובץ גדול מדי — המקסימום הוא 20 MB.")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: Exception) {
            return Result.Error("לא ניתן לפתוח את הקובץ.")
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Result.Error("הקובץ אינו תמונה תקינה (PNG או JPEG).")
        }

        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        val longEdge = max(originalW, originalH)
        if (longEdge < MIN_LONG_EDGE) {
            return Result.Error("התמונה קטנה מדי — לפחות $MIN_LONG_EDGE פיקסלים בצד הארוך.")
        }

        val sampleSize = computeSampleSize(originalW, originalH, MAX_LONG_EDGE)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = try {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        } ?: return Result.Error("לא ניתן לקרוא את התמונה.")

        val bitmap = if (max(decoded.width, decoded.height) > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / max(decoded.width, decoded.height)
            val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val finalW = bitmap.width
        val finalH = bitmap.height
        val wasResized = finalW != originalW || finalH != originalH
        val pngBytes = try {
            bitmapToPng(bitmap)
        } finally {
            bitmap.recycle()
        }

        return Result.Ok(
            Preview(
                pngBytes = pngBytes,
                width = finalW,
                height = finalH,
                originalWidth = originalW,
                originalHeight = originalH,
                wasResized = wasResized,
            ),
        )
    }

    private fun computeSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / sample > maxLongEdge * 2) {
            sample *= 2
        }
        return sample
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) {
            error("שגיאה בשמירת התמונה.")
        }
        return out.toByteArray()
    }
}
