package com.appblish.jgallery.feature.viewer

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.thumbs.fullImageRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * One video page (spec §5, design W1-09): poster frame with explicit play — WITH sound (design §3)
 * — then Media3/ExoPlayer through the boundary-routed [createMediaSource]. Controls are Compose,
 * not `PlayerView`, so gesture ownership stays deterministic: the scrub slider consumes its own
 * drags (scrubbing can never fight the pager) and taps toggle chrome+controls, auto-hidden after
 * 3s of playback. Swiping away releases the player and the page returns to its poster.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPage(
    item: MediaItem,
    createMediaSource: () -> MediaSource,
    isSettledPage: Boolean,
    chromeVisible: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
) {
    val context = LocalContext.current
    var player by remember(item.id) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var ended by remember(item.id) { mutableStateOf(false) }
    var firstFrameRendered by remember(item.id) { mutableStateOf(false) }
    var aspect by remember(item.id) { mutableFloatStateOf(item.aspectRatioOrZero()) }
    var durationMs by remember(item.id) { mutableLongStateOf(item.durationMillis) }
    var positionMs by remember(item.id) { mutableLongStateOf(0L) }
    var scrubFraction by remember(item.id) { mutableStateOf<Float?>(null) }
    var videoError by remember(item.id) { mutableStateOf<VideoError?>(null) }
    val currentChromeVisible by rememberUpdatedState(chromeVisible)

    fun startPlayback() {
        ended = false
        val existing = player
        if (existing != null) {
            existing.play()
            return
        }
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true, // explicit play with sound — take focus like a video app
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            ended = true
                            onChromeVisibleChange(true)
                        }
                        if (state == Player.STATE_READY) {
                            duration.takeIf { it > 0 }?.let { durationMs = it }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        firstFrameRendered = true
                    }

                    // Codec missing / decode failed (spec §8, W3-05). Record the reason; the effect
                    // below tears the half-open decoder down so we fall back to poster + error card.
                    override fun onPlayerError(error: PlaybackException) {
                        videoError = error.toVideoError()
                    }
                })
                setMediaSource(createMediaSource())
                prepare()
                playWhenReady = true
            }
    }

    // Swiped off this page → release the decoder and fall back to the poster. Also clears any
    // codec error, so swiping back re-attempts playback fresh (the page's implicit retry).
    LaunchedEffect(isSettledPage) {
        if (!isSettledPage) {
            player?.release()
            player = null
            isPlaying = false
            firstFrameRendered = false
            positionMs = 0L
            videoError = null
        }
    }
    // A codec/decode error came in → tear the half-open player down (outside the listener callback)
    // so the page settles on poster + error card and never keeps a broken decoder around.
    LaunchedEffect(videoError) {
        if (videoError != null) {
            player?.release()
            player = null
            isPlaying = false
            firstFrameRendered = false
        }
    }
    // App leaves the foreground → pause (audio must not keep playing over other apps).
    LifecycleStartEffect(item.id) {
        onStopOrDispose { player?.pause() }
    }
    DisposableEffect(item.id) {
        onDispose {
            player?.release()
            player = null
        }
    }
    // Progress ticker for the scrub bar — cheap poll, only while a player exists.
    LaunchedEffect(player) {
        while (isActive && player != null) {
            player?.let { positionMs = it.currentPosition.coerceAtLeast(0L) }
            delay(POSITION_POLL_MS)
        }
    }
    // Design §3: video controls auto-hide after 3s of uninterrupted playback (not mid-scrub).
    LaunchedEffect(chromeVisible, isPlaying, scrubFraction) {
        if (chromeVisible && isPlaying && scrubFraction == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            onChromeVisibleChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onChromeVisibleChange(!currentChromeVisible) })
            }
            .testTag("viewer_video_page"),
        contentAlignment = Alignment.Center,
    ) {
        player?.let { activePlayer ->
            AndroidView(
                factory = { SurfaceView(it) },
                update = { surface -> activePlayer.setVideoSurfaceView(surface) },
                modifier = Modifier.aspectRatio(if (aspect > 0f) aspect else DEFAULT_ASPECT),
            )
        }
        if (player == null || !firstFrameRendered) {
            VideoPoster(item)
        }

        val showPlayOverlay =
            videoError == null && (player == null || ended || (chromeVisible && !ended))
        if (showPlayOverlay) {
            IconButton(
                onClick = {
                    when {
                        player == null || ended -> {
                            if (ended) player?.seekTo(0)
                            startPlayback()
                        }
                        isPlaying -> player?.pause()
                        else -> player?.play()
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .testTag("viewer_play_pause"),
            ) {
                Icon(
                    imageVector = when {
                        ended -> Icons.Filled.Replay
                        player != null && isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = if (player != null && isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        if (videoError == null && player != null && chromeVisible) {
            VideoControls(
                positionMs = positionMs,
                durationMs = durationMs,
                scrubFraction = scrubFraction,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    val duration = durationMs
                    scrubFraction?.let { fraction ->
                        if (duration > 0) player?.seekTo((fraction * duration).toLong())
                    }
                    scrubFraction = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            )
        }

        // Graceful §8 fallback: an undecodable clip shows the poster behind a card, never a crash.
        videoError?.let { error ->
            VideoErrorCard(error = error, onOpenWith = onOpenWith, onInfo = onInfo)
        }
    }
}

/** Scrub bar + timestamps. The [Slider] consumes its drags, so scrubbing never moves the pager. */
@Composable
private fun VideoControls(
    positionMs: Long,
    durationMs: Long,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("viewer_video_controls")) {
        val playedFraction =
            if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Slider(
            value = scrubFraction ?: playedFraction,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            colors = SliderDefaults.colors(
                thumbColor = JGalleryColors.Accent,
                activeTrackColor = JGalleryColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.38f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            val scrubMs = scrubFraction?.let { (it * durationMs).toLong() }
            Text(
                text = formatPlaybackTime(scrubMs ?: positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatPlaybackTime(durationMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Poster: the grid thumbnail instantly, sharpened by a boundary-routed full-size frame decode. */
@Composable
private fun VideoPoster(item: MediaItem) {
    val context = LocalPlatformContext.current
    val placeholderRequest = remember(item.id, item.dateModifiedMillis) {
        ImageRequest.Builder(context)
            .data(item.thumbnailRequest())
            .size(POSTER_PLACEHOLDER_EDGE_PX)
            .build()
    }
    AsyncImage(
        model = placeholderRequest,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
    AsyncImage(
        model = item.fullImageRequest(), // VideoFrameDecoder serves the sharp frame
        contentDescription = item.displayName,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}

internal fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private const val POSITION_POLL_MS = 250L
private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val POSTER_PLACEHOLDER_EDGE_PX = 384
private const val DEFAULT_ASPECT = 16f / 9f
