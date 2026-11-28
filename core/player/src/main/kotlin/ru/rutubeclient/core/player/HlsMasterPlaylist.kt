package ru.rutubeclient.core.player

/** One rendition of an HLS master playlist. */
data class HlsVariant(val height: Int, val url: String)

/**
 * Extracts `(height, url)` pairs from an HLS master playlist. Each rendition is
 * typically listed twice (CDN mirrors); only one URL per height is kept.
 */
fun parseMasterPlaylistVariants(master: String): List<HlsVariant> {
    val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""")
    val variants = mutableListOf<HlsVariant>()
    var pendingHeight = 0
    for (line in master.lineSequence()) {
        when {
            line.startsWith("#EXT-X-STREAM-INF") ->
                pendingHeight = resolution.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0

            line.startsWith("#") -> Unit

            line.isNotBlank() -> {
                if (pendingHeight > 0) variants.add(HlsVariant(pendingHeight, line.trim()))
                pendingHeight = 0
            }
        }
    }
    return variants.distinctBy { it.height }
}

/**
 * Picks the variant for [qualityHeight]: exact height match, else the closest
 * height `<=` target, else the lowest. [qualityHeight] `<= 0` means best
 * available (highest height).
 */
fun selectHlsVariant(variants: List<HlsVariant>, qualityHeight: Int): HlsVariant? {
    if (variants.isEmpty()) return null
    if (qualityHeight <= 0) return variants.maxByOrNull { it.height }
    return variants.firstOrNull { it.height == qualityHeight }
        ?: variants.filter { it.height <= qualityHeight }.maxByOrNull { it.height }
        ?: variants.minByOrNull { it.height }
}
