package ru.rutubeclient.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsMasterPlaylistTest {

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=361000,RESOLUTION=256x104
        https://cdn-a.example/0.m3u8?i=256x104_361
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=361000,RESOLUTION=256x104
        https://cdn-b.example/0.m3u8?i=256x104_361
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=679000,RESOLUTION=424x176
        https://cdn-a.example/1.m3u8?i=424x176_679
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=679000,RESOLUTION=424x176
        https://cdn-b.example/1.m3u8?i=424x176_679
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=3146000,RESOLUTION=1920x800
        https://cdn-a.example/2.m3u8?i=1920x800_3146
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=3146000,RESOLUTION=1920x800
        https://cdn-b.example/2.m3u8?i=1920x800_3146
    """.trimIndent()

    @Test
    fun `parses variants and dedupes CDN mirrors by height`() {
        val variants = parseMasterPlaylistVariants(master)
        assertEquals(listOf(104, 176, 800), variants.map { it.height })
        // Each height keeps exactly one URL (first mirror).
        assertEquals("https://cdn-a.example/0.m3u8?i=256x104_361", variants[0].url)
    }

    @Test
    fun `selects exact height match`() {
        val variants = parseMasterPlaylistVariants(master)
        assertEquals(176, selectHlsVariant(variants, 176)?.height)
    }

    @Test
    fun `selects closest height at or below target when no exact match`() {
        val variants = parseMasterPlaylistVariants(master)
        // 144 isn't in the list; the closest <= 144 is 104.
        assertEquals(104, selectHlsVariant(variants, 144)?.height)
    }

    @Test
    fun `selects lowest height when target is below everything`() {
        val variants = parseMasterPlaylistVariants(master)
        assertEquals(104, selectHlsVariant(variants, 50)?.height)
    }

    @Test
    fun `selects highest height for best available`() {
        val variants = parseMasterPlaylistVariants(master)
        assertEquals(800, selectHlsVariant(variants, 0)?.height)
    }

    @Test
    fun `returns null for empty variants`() {
        assertNull(selectHlsVariant(emptyList(), 144))
        assertNull(selectHlsVariant(emptyList(), 0))
    }

    @Test
    fun `ignores variants without a resolvable height`() {
        val variants = parseMasterPlaylistVariants(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nhttps://x/1.m3u8\n"
        )
        assertEquals(emptyList<HlsVariant>(), variants)
    }
}
