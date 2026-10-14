package com.couchzap.iptv.player

/** Playback facts are independent of the focused or selected list row. */
enum class ChannelPlaybackStatus { CONNECTING, BUFFERING, PLAYING, PAUSED, UNAVAILABLE, ENDED }
