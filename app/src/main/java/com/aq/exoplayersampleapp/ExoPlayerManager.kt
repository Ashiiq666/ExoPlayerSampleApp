package com.aq.exoplayersampleapp

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

class ExoPlayerManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var player: ExoPlayer? = null
    private var currentUri: Uri? = null

    fun initializePlayer(): ExoPlayer {
        if (player == null) {
            val okHttpClient = OkHttpClient()
            val dataSourceFactory = DefaultDataSourceFactory(
                context,
                OkHttpDataSource.Factory(okHttpClient)
            )
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = false
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            // Handle playback state changes if needed
                        }
                    })
                }
        }
        return player!!
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


    fun release() {
        player?.release()
        player = null
        currentUri = null
    }
}

