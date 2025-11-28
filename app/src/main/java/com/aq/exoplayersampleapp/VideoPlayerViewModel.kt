package com.aq.exoplayersampleapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1f,
    val isLoading: Boolean = false
)

class VideoPlayerViewModel : ViewModel() {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _speedOptions = MutableStateFlow(listOf(0.5f, 1f, 1.5f, 2f))
    val speedOptions: StateFlow<List<Float>> = _speedOptions.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    fun initializePlayer(player: ExoPlayer) {
        this.exoPlayer = player

        // Add listener for player state changes
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                updatePlaybackState { it.copy(isLoading = isLoading) }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                updatePlaybackState { it.copy(playbackSpeed = playbackParameters.speed) }
            }
        })

        // Update position and duration periodically
        viewModelScope.launch {
            while (true) {
                updatePlaybackState { state ->
                    state.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration,
                        bufferedPercentage = player.bufferedPercentage
                    )
                }
                delay(100)
            }
        }
    }

    fun playPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun skipForward(seconds: Long = 10) {
        exoPlayer?.let {
            val newPosition = (it.currentPosition + (seconds * 1000)).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }

    fun skipBackward(seconds: Long = 10) {
        exoPlayer?.let {
            val newPosition = (it.currentPosition - (seconds * 1000)).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }

    private fun updatePlaybackState(update: (PlaybackState) -> PlaybackState) {
        _playbackState.value = update(_playbackState.value)
    }

    override fun onCleared() {
        exoPlayer?.release()
        super.onCleared()
    }
}

