package com.couchzap.iptv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PlaylistRepositoryTest {
    private lateinit var repository: PlaylistRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = PlaylistRepository(context)
        repository.clearPlaylist()
    }

    @Test fun `invalid replacement keeps the previous playlist and last channel`() = runBlocking {
        repository.importPlaylistFromStream("One,https://example.org/one\nTwo,https://example.org/two".byteInputStream())
        repository.saveLastChannelIndex(1)
        assertTrue(repository.importPlaylistFromStream("invalid".byteInputStream()).isEmpty())
        assertEquals(listOf("One", "Two"), repository.loadCachedChannels().map { it.name })
        assertEquals(1, repository.getLastChannelIndex())
        assertTrue(repository.isPlaylistReady())
    }

    @Test fun `failed read closes the input and keeps the previous playlist`() = runBlocking {
        repository.importPlaylistFromStream("One,https://example.org/one".byteInputStream())
        var closed = false
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Disconnected")
            override fun close() { closed = true }
        }
        try {
            repository.importPlaylistFromStream(broken)
            fail("Import must fail")
        } catch (_: IOException) { }
        assertTrue(closed)
        assertEquals("One", repository.loadCachedChannels().single().name)
    }

    @Test fun `successful replacement closes input and resets last channel`() = runBlocking {
        repository.saveLastChannelIndex(10)
        var closed = false
        val input = object : ByteArrayInputStream("New,https://example.org/new".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        repository.importPlaylistFromStream(input)
        assertTrue(closed)
        assertEquals("New", repository.loadCachedChannels().single().name)
        assertEquals(0, repository.getLastChannelIndex())
    }
}
