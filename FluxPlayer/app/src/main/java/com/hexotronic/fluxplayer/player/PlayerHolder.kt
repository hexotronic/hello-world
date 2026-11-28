package com.hexotronic.fluxplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.net.StreamResolver
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegMediaSourceFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/** HTTP-Header des aktuell abgespielten Eintrags (Referer, User-Agent …). */
object HeaderStore {
    @Volatile var headers: Map<String, String> = emptyMap()
}

/**
 * Prozessweiter ExoPlayer (wird von Activity und MediaSessionService gemeinsam genutzt).
 */
@OptIn(UnstableApi::class)
object PlayerHolder {
    private var player: ExoPlayer? = null
    var decoderManager: DecoderManager? = null
        private set

    const val EXTRA_HEADERS_KEYS = "flux.headerKeys"
    const val EXTRA_HEADERS_VALUES = "flux.headerValues"
    const val EXTRA_FFMPEG = "flux.ffmpeg"

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val prefs = Prefs(app)

        val ok = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val http = OkHttpDataSource.Factory(ok).setUserAgent(prefs.userAgent)
        val withHeaders = ResolvingDataSource.Factory(http) { spec ->
            val h = HeaderStore.headers
            if (h.isEmpty()) spec else spec.withAdditionalHeaders(h)
        }
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(app, withHeaders)

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        val defaultSources = DefaultMediaSourceFactory(dataSourceFactory, extractors)
        val sources = FluxMediaSourceFactory(defaultSources, FfmpegMediaSourceFactory(dataSourceFactory))

        val dm = DecoderManager(modeFor(prefs.decoder), DecoderMode.AUTO)
        decoderManager = dm
        val renderers = NextRenderersFactory(app)
            .setDecoderManager(dm)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val trackSelector = DefaultTrackSelector(app).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage(Locale.getDefault().language)
                .setPreferredTextLanguage(Locale.getDefault().language)
                .setSelectUndeterminedTextLanguage(false)
                .build()
        }

        val big = prefs.largeBuffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (big) 60_000 else 25_000,
                if (big) 180_000 else 60_000,
                1_500,
                3_000
            )
            .build()

        val p = ExoPlayer.Builder(app, renderers)
            .setMediaSourceFactory(sources)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(prefs.seekStepSeconds * 1000L)
            .setSeekForwardIncrementMs(prefs.seekStepSeconds * 1000L)
            .build()
        dm.attach(p)
        player = p
        return p
    }

    fun setDecoder(pref: DecoderPreference) {
        decoderManager?.selectVideoDecoder(modeFor(pref))
    }

    private fun modeFor(pref: DecoderPreference): DecoderMode = when (pref) {
        DecoderPreference.AUTO -> DecoderMode.AUTO
        DecoderPreference.HARDWARE -> DecoderMode.HARDWARE
        DecoderPreference.SOFTWARE -> DecoderMode.FFMPEG
    }

    fun buildMediaItem(entry: MediaEntry): MediaItem {
        val extras = android.os.Bundle().apply {
            putStringArray(EXTRA_HEADERS_KEYS, entry.headers.keys.toTypedArray())
            putStringArray(EXTRA_HEADERS_VALUES, entry.headers.values.toTypedArray())
            val ext = StreamResolver.extensionOf(entry.uri)
            putBoolean(EXTRA_FFMPEG, entry.forceFfmpeg || ext in StreamResolver.FFMPEG_EXT)
        }
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(entry.uri))
            .setMediaId(entry.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.title)
                    .setDisplayTitle(entry.title)
                    .setExtras(extras)
                    .build()
            )
        entry.mimeType?.let { builder.setMimeType(it) }
        return builder.build()
    }

    fun headersOf(item: MediaItem?): Map<String, String> {
        val extras = item?.mediaMetadata?.extras ?: return emptyMap()
        val k = extras.getStringArray(EXTRA_HEADERS_KEYS) ?: return emptyMap()
        val v = extras.getStringArray(EXTRA_HEADERS_VALUES) ?: return emptyMap()
        return k.indices.associate { k[it] to (v.getOrNull(it) ?: "") }
    }

    fun withFfmpeg(item: MediaItem): MediaItem {
        val extras = android.os.Bundle(item.mediaMetadata.extras ?: android.os.Bundle())
        extras.putBoolean(EXTRA_FFMPEG, true)
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    fun usesFfmpeg(item: MediaItem?): Boolean = item?.mediaMetadata?.extras?.getBoolean(EXTRA_FFMPEG) == true
}

/** Wählt pro Eintrag zwischen ExoPlayer-Extraktoren und dem FFmpeg-Extraktor (AVI/WMV/RMVB …). */
@OptIn(UnstableApi::class)
class FluxMediaSourceFactory(
    private val default: DefaultMediaSourceFactory,
    private val ffmpeg: FfmpegMediaSourceFactory
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        default.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        default.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (PlayerHolder.usesFfmpeg(mediaItem)) ffmpeg.createMediaSource(mediaItem)
        else default.createMediaSource(mediaItem)
}
