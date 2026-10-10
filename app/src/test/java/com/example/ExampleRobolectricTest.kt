package com.couchzap.iptv

import com.couchzap.iptv.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelUnitTest {

  @Test
  fun testChannelCreation() {
    val channel = Channel(
      number = 1,
      name = "BBC News",
      url = "http://example.com/stream.m3u8"
    )
    assertEquals(1, channel.number)
    assertEquals("BBC News", channel.name)
    assertEquals("http://example.com/stream.m3u8", channel.url)
  }
}
