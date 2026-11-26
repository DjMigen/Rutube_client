package ru.rutubeclient.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Tests run against real API responses captured into test resources,
 * plus a MockWebServer for HTTP behaviour (URLs, errors).
 */
class RuTubeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RuTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RuTubeApi(baseUrl = server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "fixture $name not found"
        }.readText()

    private fun enqueueFixture(name: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture(name)))
    }

    @Test
    fun `home feed parses`() = runTest {
        enqueueFixture("home_feed.json")
        val page = api.getHomeFeed(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        val video = page.items.first()
        assertTrue(video.id.isNotBlank())
        assertTrue(video.title.isNotBlank())
        assertNotNull(video.author)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/v2/video/recommendation/main?page=1")
        )
    }

    @Test
    fun `search parses and encodes query`() = runTest {
        enqueueFixture("search.json")
        val page = api.searchVideos("новости", 1)
        assertTrue(page.items.isNotEmpty())
        val video = page.items.first()
        assertTrue(video.title.isNotBlank())
        assertTrue(video.hits >= 0)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/search/video/?query=")
        )
    }

    @Test
    fun `video details parse`() = runTest {
        enqueueFixture("video_details.json")
        val details = api.getVideo("7d5ce457269008eff74ee6ce0b7a4cd3")
        assertEquals("7d5ce457269008eff74ee6ce0b7a4cd3", details.id)
        assertTrue(details.title.isNotBlank())
        assertTrue(details.durationSec > 0)
        assertNotNull(details.author)
        assertTrue(details.hits > 0)
    }

    @Test
    fun `stream info extracts signed hls url`() = runTest {
        enqueueFixture("play_options.json")
        val info = api.getStreamInfo("7d5ce457269008eff74ee6ce0b7a4cd3")
        assertTrue(info.hlsUrl.startsWith("https://"))
        assertTrue(info.hlsUrl.contains(".m3u8"))
        assertTrue(info.hlsUrl.contains("sign="))
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/play/options/7d5ce457269008eff74ee6ce0b7a4cd3/")
        )
    }

    @Test
    fun `stream info throws when no balancer url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"video_balancer":{}}"""))
        try {
            api.getStreamInfo("abc")
            fail("expected StreamUnavailableException")
        } catch (e: StreamUnavailableException) {
            // expected
        }
    }

    @Test
    fun `comments parse`() = runTest {
        enqueueFixture("comments.json")
        val page = api.getComments("7d5ce457269008eff74ee6ce0b7a4cd3", 1)
        assertTrue(page.items.isNotEmpty())
        val comment = page.items.first()
        assertTrue(comment.text.isNotBlank())
        assertNotNull(comment.authorName)
    }

    @Test
    fun `categories parse`() = runTest {
        enqueueFixture("categories.json")
        val categories = api.getCategories()
        assertTrue(categories.size > 5)
        assertTrue(categories.all { it.name.isNotBlank() })
        assertTrue(categories.all { it.id > 0 })
    }

    @Test
    fun `channel videos parse`() = runTest {
        enqueueFixture("channel_videos.json")
        val page = api.getChannelVideos(23872149L, 1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/video/person/23872149/")
        )
    }

    @Test
    fun `tv shows parse`() = runTest {
        enqueueFixture("tv_shows.json")
        val page = api.getTvShows(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        val show = page.items.first { it.id > 0 }
        assertTrue(show.name.isNotBlank())
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/metainfo/tv/?page=1")
        )
    }

    @Test
    fun `tv show details parse`() = runTest {
        enqueueFixture("tv_show.json")
        val show = api.getTvShow(8)
        assertEquals(8, show.id)
        assertTrue(show.name.isNotBlank())
        assertTrue(show.seasonsCount >= 0)
        assertNotNull(show.posterUrl)
    }

    @Test
    fun `tv show episodes parse`() = runTest {
        enqueueFixture("tv_show_videos.json")
        val page = api.getTvShowVideos(8, 1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        val video = page.items.first()
        assertTrue(video.id.isNotBlank())
        assertTrue(video.title.isNotBlank())
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/metainfo/tv/8/video?page=1")
        )
    }

    @Test
    fun `top movies parse`() = runTest {
        enqueueFixture("top_movie.json")
        val page = api.getTopMovies(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/v2/video/recommendation/main/top_movie")
        )
    }

    @Test
    fun `top serials parse`() = runTest {
        enqueueFixture("top_serial.json")
        val page = api.getTopSerials(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/v2/video/recommendation/main/top_serial")
        )
    }

    @Test
    fun `top cartoons parse`() = runTest {
        enqueueFixture("top_cartoon.json")
        val page = api.getTopCartoons(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/v2/video/recommendation/main/top_cartoon")
        )
    }

    @Test
    fun `top anime parse`() = runTest {
        enqueueFixture("top_anime.json")
        val page = api.getTopAnime(1)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.hasNext)
        assertTrue(
            server.takeRequest().path.orEmpty()
                .startsWith("/api/v2/video/recommendation/main/top_anime")
        )
    }

    @Test
    fun `http error throws RuTubeApiException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"not found"}"""))
        try {
            api.getVideo("missing")
            fail("expected RuTubeApiException")
        } catch (e: RuTubeApiException) {
            assertEquals(404, e.code)
        }
    }
}
