package com.aq.exoplayersampleapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoPlayerUiState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFullscreen: Boolean = false,
    val showControls: Boolean = true,
    val isDragging: Boolean = false,
    val seekPosition: Long = 0L,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0: Off, 1: One, 2: All
    val currentVideoQuality: String = "" // e.g., "1080p", "720p", "480p"
)

class VideoPlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    fun updatePlaying(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    fun updatePosition(currentPosition: Long, duration: Long) {
        _uiState.value = _uiState.value.copy(
            currentPosition = currentPosition,
            duration = duration
        )
    }

    fun updatePlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun updateLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    fun updateError(error: String?) {
        _uiState.value = _uiState.value.copy(error = error)
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen
        )
    }

    fun setFullscreen(isFullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = isFullscreen)
    }

    fun showControls() {
        _uiState.value = _uiState.value.copy(showControls = true)
    }

    fun hideControls() {
        _uiState.value = _uiState.value.copy(showControls = false)
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(
            showControls = !_uiState.value.showControls
        )
    }

    fun setDragging(isDragging: Boolean) {
        _uiState.value = _uiState.value.copy(isDragging = isDragging)
    }

    fun setSeekPosition(position: Long) {
        _uiState.value = _uiState.value.copy(seekPosition = position)
    }

    fun setVolume(volume: Int) {
        _uiState.value = _uiState.value.copy(volume = volume.coerceIn(0, 100))
    }

    fun toggleMute() {
        _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted)
    }

    fun toggleShuffle() {
        _uiState.value = _uiState.value.copy(isShuffleEnabled = !_uiState.value.isShuffleEnabled)
    }

    fun toggleRepeat() {
        val nextMode = (_uiState.value.repeatMode + 1) % 3
        _uiState.value = _uiState.value.copy(repeatMode = nextMode)
    }

    fun updateVideoQuality(quality: String) {
        _uiState.value = _uiState.value.copy(currentVideoQuality = quality)
    }
}
