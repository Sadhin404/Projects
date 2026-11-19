package com.shadhinkhan.breastai.core

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Gentle vibration feedback (his order): a little double-buzz when an
 *  image is refused or an analysis fails, a soft pulse on completion. */
object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        context.getSystemService(Vibrator::class.java)

    fun fail(context: Context) {
        try {
            vibrator(context)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 55, 90, 55), -1)
            )
        } catch (_: Exception) {
            // No vibrator on this device — nothing to do.
        }
    }

    fun success(context: Context) {
        try {
            vibrator(context)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 70, 110, 40), -1)
            )
        } catch (_: Exception) {
            // No vibrator on this device — nothing to do.
        }
    }
}
