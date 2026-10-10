package com.couchzap.iptv.util

import android.view.KeyEvent

object Keys {

    fun isNextChannelKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP
    }

    fun isPrevChannelKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN
    }

    fun isDigitKey(keyCode: Int): Boolean {
        return (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) ||
                (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9)
    }

    fun getDigitFromKey(keyCode: Int): Int {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0
        }
        if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0
        }
        return -1
    }

    fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }

    fun isInfoKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_INFO || keyCode == 221
    }

    fun isPlayPauseKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
    }

    fun isMenuKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MENU
    }

    fun isBackKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
    }
}
