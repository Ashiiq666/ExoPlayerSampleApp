package com.aq.exoplayersampleapp

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
import okhttp3.OkHttpClient

class ExoPlayerManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var player: ExoPlayer? = null
    private var currentUri: Uri? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var qualityChangeListener: ((VideoSize) -> Unit)? = null

    fun setQualityChangeListener(listener: (VideoSize) -> Unit) {
        qualityChangeListener = listener
    }

    fun initializePlayer(): ExoPlayer {
        if (player == null) {
            // Create adaptive track selection factory
            val trackSelectionFactory = AdaptiveTrackSelection.Factory()
            
            // Initialize DefaultTrackSelector with adaptive selection
            trackSelector = DefaultTrackSelector(context, trackSelectionFactory)
            
            // Configure track selection parameters
            configureTrackSelector(trackSelector!!)
            
            // Create optimized load control for adaptive streaming
            val loadControl = createOptimizedLoadControl()
            
            // Setup data source factory
            val okHttpClient = OkHttpClient()
            val dataSourceFactory = DefaultDataSourceFactory(
                context,
                OkHttpDataSource.Factory(okHttpClient)
            )
            
            // Create media source factory with DASH, HLS, and SmoothStreaming support
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            
            // Build player with adaptive streaming configuration
            player = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = false
                    
                    // Add analytics listener for quality changes
                    addAnalyticsListener(object : AnalyticsListener {
                        override fun onVideoSizeChanged(
                            eventTime: AnalyticsListener.EventTime,
                            videoSize: VideoSize
                        ) {
                            qualityChangeListener?.invoke(videoSize)
                        }
                    })
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            // Handle playback state changes if needed
                        }
                    })
                }
            
            // Adjust quality based on network conditions
            adjustQualityForNetwork()
        }
        return player!!
    }
    
    private fun configureTrackSelector(trackSelector: DefaultTrackSelector) {
        val parameters = trackSelector.buildUponParameters()
            // Video constraints - can be customized
            .setMaxVideoSize(1920, 1080) // Max 1080p
            .setMaxVideoBitrate(5_000_000) // 5 Mbps cap
            // Audio preferences
            .setPreferredAudioLanguage("en")
            // Subtitle preferences
            .setPreferredTextLanguage("en")
            .setSelectUndeterminedTextLanguage(true)
            // Performance tuning
            .setForceHighestSupportedBitrate(false)
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .build()
        
        trackSelector.setParameters(parameters)
    }
    
    private fun createOptimizedLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,  // minBufferMs - prevents drip-feeding
                60_000,  // maxBufferMs - higher for stable connections
                2_000,   // bufferForPlaybackMs - start quickly
                5_000    // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(true) // Better for live streams
            .build()
    }
    
    private fun adjustQualityForNetwork() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        
        val maxBitrate = when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                5_000_000 // 5 Mbps for WiFi
            }
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                2_000_000 // 2 Mbps for cellular
            }
            else -> {
                1_000_000 // 1 Mbps for unknown/slow connections
            }
        }
        
        trackSelector?.setParameters(
            trackSelector!!.buildUponParameters()
                .setMaxVideoBitrate(maxBitrate)
                .build()
        )
    }
    
    fun adjustQualityForLowMemory() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.isLowRamDevice) {
            trackSelector?.setParameters(
                trackSelector!!.buildUponParameters()
                    .setMaxVideoSize(1280, 720) // Max 720p for low memory devices
                    .setMaxVideoBitrate(1_500_000) // 1.5 Mbps cap
                    .build()
            )
        }
    }

    fun loadVideo(uri: Uri) {
        if (currentUri != uri) {
            currentUri = uri
            player?.let { exoPlayer ->
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
        }
    }

    fun getPlayer(): ExoPlayer? = player

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun seekForward(seconds: Long = 10_000L) {
        player?.let {
            val newPosition = (it.currentPosition + seconds).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }

    fun seekBackward(seconds: Long = 10_000L) {
        player?.let {
            val newPosition = (it.currentPosition - seconds).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.let {
            it.playbackParameters = it.playbackParameters.withSpeed(speed)
        }
    }


    fun getTrackSelector(): DefaultTrackSelector? = trackSelector
    
    fun updateNetworkQuality() {
        adjustQualityForNetwork()
    }
    
    fun release() {
        player?.release()
        player = null
        trackSelector = null
        currentUri = null
        qualityChangeListener = null
    }
}

