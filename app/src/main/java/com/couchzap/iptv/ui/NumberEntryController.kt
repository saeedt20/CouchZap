package com.couchzap.iptv.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

class NumberEntryController(
    private val tvDigitGhost: TextView,
    private val tvToastMsg: TextView,
    private val totalChannelCount: () -> Int,
    private val onCommitChannelNumber: (Int) -> Unit
) {

    private val digitBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())

    private val commitRunnable = Runnable {
        commitDigits()
    }

    private val hideToastRunnable = Runnable {
        tvToastMsg.visibility = View.GONE
    }

    fun isTyping(): Boolean = digitBuffer.isNotEmpty()

    fun appendDigit(digit: Int) {
        if (digitBuffer.length >= totalChannelCount().toString().length.coerceAtLeast(4)) return

        digitBuffer.append(digit)
        updateDigitOverlay()

        handler.removeCallbacks(commitRunnable)

        val targetNum = digitBuffer.toString().toIntOrNull() ?: 0
        val maxChannels = totalChannelCount()
        val totalDigits = maxChannels.toString().length

        // Check if buffer length matches total digits count or if user hit max
        if (digitBuffer.length >= totalDigits && targetNum in 1..maxChannels) {
            commitDigits()
        } else {
            handler.postDelayed(commitRunnable, 1800L)
        }
    }

    fun deleteLastDigit(): Boolean {
        if (digitBuffer.isNotEmpty()) {
            digitBuffer.deleteCharAt(digitBuffer.length - 1)
            handler.removeCallbacks(commitRunnable)
            if (digitBuffer.isNotEmpty()) {
                updateDigitOverlay()
                handler.postDelayed(commitRunnable, 1800L)
            } else {
                tvDigitGhost.visibility = View.GONE
            }
            return true
        }
        return false
    }

    fun commitDigits() {
        handler.removeCallbacks(commitRunnable)
        if (digitBuffer.isEmpty()) return

        val number = digitBuffer.toString().toIntOrNull() ?: -1
        val maxChannels = totalChannelCount()

        digitBuffer.clear()
        tvDigitGhost.visibility = View.GONE

        if (number in 1..maxChannels) {
            onCommitChannelNumber(number)
        } else {
            flashInvalidChannelMessage(number)
        }
    }

    private fun updateDigitOverlay() {
        val display = digitBuffer.toString().toCharArray().joinToString(" ") + " _"
        tvDigitGhost.text = display
        tvDigitGhost.visibility = View.VISIBLE
    }

    private fun flashInvalidChannelMessage(number: Int) {
        tvToastMsg.text = tvToastMsg.context.getString(com.couchzap.iptv.R.string.no_channel_number, number.toString())
        tvToastMsg.visibility = View.VISIBLE
        handler.removeCallbacks(hideToastRunnable)
        handler.postDelayed(hideToastRunnable, 1200L)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        tvToastMsg.visibility = View.GONE
        digitBuffer.clear()
        tvDigitGhost.visibility = View.GONE
    }
}
