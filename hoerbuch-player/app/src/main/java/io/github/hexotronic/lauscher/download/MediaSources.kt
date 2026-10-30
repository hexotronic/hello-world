package io.github.hexotronic.lauscher.download

import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Video and music platforms that can be searched and downloaded from (via NewPipe Extractor). */
enum class Source(val label: String, private val searchFilter: String?) {
    YOUTUBE("YouTube", "videos"),
    PEERTUBE("PeerTube", "videos"),
    SOUNDCLOUD("SoundCloud", "tracks"),
    BANDCAMP("Bandcamp", null),
    MEDIA_CCC("media.ccc.de", "all"),
    ;

    val service: StreamingService
        get() = when (this) {
            YOUTUBE -> ServiceList.YouTube
            PEERTUBE -> ServiceList.PeerTube
            SOUNDCLOUD -> ServiceList.SoundCloud
            BANDCAMP -> ServiceList.Bandcamp
            MEDIA_CCC -> ServiceList.MediaCCC
        }

    val filters: List<String> get() = listOfNotNull(searchFilter)
}

/** A page of videos or tracks; [more] loads the next page (null at the end). */
class Listing(val title: String?, val items: List<MediaItem>, val more: (() -> Listing)?)

/** Where the sound of one item comes from. [isVideo]: a video file whose audio track is used. */
data class AudioSource(
    val url: String,
    val extension: String,
    val isVideo: Boolean,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
)

object MediaSources {
    @Volatile
    private var ready = false

    private fun init() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(ExtractorDownloader(), Localization("de", "DE"), ContentCountry("DE"))
                ready = true
            }
        }
    }

    /** True if [url] belongs to one of the supported platforms (otherwise it may be a plain file link). */
    fun isSupported(url: String): Boolean {
        init()
        return runCatching { NewPipe.getServiceByUrl(url).getLinkTypeByUrl(url) != LinkType.NONE }.getOrDefault(false)
    }

    fun search(source: Source, query: String): Listing {
        init()
        val service = source.service
        val handler = service.searchQHFactory.fromQuery(query, source.filters, "")
        val info = SearchInfo.getInfo(service, handler)
        val title = "${source.label}: $query"
        fun page(next: Page?): (() -> Listing)? = next?.let { p ->
            {
                val more = SearchInfo.getMoreItems(service, handler, p)
                Listing(title, streams(more.items), if (more.hasNextPage()) page(more.nextPage) else null)
            }
        }
        return Listing(title, streams(info.relatedItems), if (info.hasNextPage()) page(info.nextPage) else null)
    }

    /** A video, playlist or channel link; plain file links become a single item. */
    fun resolve(url: String): Listing {
        init()
        val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrNull()
        val type = service?.let { runCatching { it.getLinkTypeByUrl(url) }.getOrNull() } ?: LinkType.NONE
        if (service == null || type == LinkType.NONE) {
            if (!url.startsWith("http")) error("Kein gültiger Link")
            val name = LinkInput.fileNameOf(url)
            return Listing(name, listOf(MediaItem(url, name)), null)
        }
        return when (type) {
            LinkType.STREAM -> {
                val info = StreamInfo.getInfo(service, url)
                Listing(info.name, listOf(MediaItem(info.url, info.name, info.uploaderName, info.duration, thumbnail(info.thumbnails))), null)
            }
            LinkType.PLAYLIST -> {
                val info = PlaylistInfo.getInfo(service, url)
                fun page(next: Page?): (() -> Listing)? = next?.let { p ->
                    {
                        val more = PlaylistInfo.getMoreItems(service, url, p)
                        Listing(info.name, streams(more.items), if (more.hasNextPage()) page(more.nextPage) else null)
                    }
                }
                Listing(info.name, streams(info.relatedItems), if (info.hasNextPage()) page(info.nextPage) else null)
            }
            LinkType.CHANNEL -> {
                val channel = ChannelInfo.getInfo(service, url)
                val tab = channel.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters || ChannelTabs.TRACKS in it.contentFilters }
                    ?: channel.tabs.firstOrNull()
                    ?: error("Der Kanal hat keine Videos")
                val info = ChannelTabInfo.getInfo(service, tab)
                fun page(next: Page?): (() -> Listing)? = next?.let { p ->
                    {
                        val more = ChannelTabInfo.getMoreItems(service, tab, p)
                        Listing(channel.name, streams(more.items), if (more.hasNextPage()) page(more.nextPage) else null)
                    }
                }
                Listing(channel.name, streams(info.relatedItems), if (info.hasNextPage()) page(info.nextPage) else null)
            }
            else -> error("Dieser Link wird nicht unterstützt")
        }
    }

    /** All items of a listing, following its pages (for "Alle laden"), at most [max]. */
    fun loadAll(first: Listing, max: Int = 500): Listing {
        val items = ArrayList(first.items)
        var next = first.more
        while (next != null && items.size < max) {
            val page = next()
            items += page.items
            next = page.more
        }
        return Listing(first.title, items.distinctBy { it.url }.take(max), next)
    }

    /**
     * The best downloadable sound of [url]: an audio-only stream, else the smallest video with sound.
     * With [compatible] M4A is preferred (kept as it is); otherwise the highest bitrate wins, since
     * the stream is decoded and re-encoded anyway.
     */
    fun audioOf(url: String, compatible: Boolean = true): AudioSource {
        init()
        if (!isSupported(url)) {
            return AudioSource(url, LinkInput.extensionOf(url).ifEmpty { "mp4" }, true, LinkInput.fileNameOf(url), null, null)
        }
        val info = StreamInfo.getInfo(url)
        val thumb = thumbnail(info.thumbnails)
        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .let { c -> if (compatible) c.thenBy { formatRank(it.format) } else c }
                    .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
                    .thenBy { formatRank(it.format) },
            )
            .firstOrNull()
        if (audio != null) {
            return AudioSource(audio.content, audio.format?.suffix ?: "m4a", false, info.name, info.uploaderName, thumb)
        }
        val video = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.isVideoOnly }
            .minByOrNull { if (it.height > 0) it.height else Int.MAX_VALUE }
            ?: error("Für „${info.name}“ gibt es keinen herunterladbaren Ton (z. B. ein Livestream)")
        return AudioSource(video.content, video.format?.suffix ?: "mp4", true, info.name, info.uploaderName, thumb)
    }

    private fun formatRank(format: MediaFormat?): Int = when (format) {
        MediaFormat.M4A -> 0
        MediaFormat.MP3 -> 1
        MediaFormat.WEBMA_OPUS, MediaFormat.OPUS, MediaFormat.WEBMA -> 2
        MediaFormat.OGG -> 3
        else -> 4
    }

    private fun streams(items: List<*>): List<MediaItem> = items.filterIsInstance<StreamInfoItem>().map {
        MediaItem(it.url, it.name, it.uploaderName, it.duration, thumbnail(it.thumbnails))
    }

    private fun thumbnail(images: List<Image>?): String? =
        images.orEmpty().filter { it.height in 1..480 }.maxByOrNull { it.height }?.url ?: images?.firstOrNull()?.url
}
