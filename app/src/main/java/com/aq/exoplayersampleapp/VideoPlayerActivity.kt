package com.aq.exoplayersampleapp

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aq.exoplayersampleapp.ui.theme.ExoPlayerSampleAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoPlayerActivity : ComponentActivity() {
    private var playerManager: ExoPlayerManager? = null
    private var playerView: PlayerView? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoItem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("video", VideoItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<VideoItem>("video")
        }

        if (videoItem == null) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize player manager
        playerManager = ExoPlayerManager(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        )
        exoPlayer = playerManager!!.initializePlayer()
        playerManager!!.loadVideo(videoItem.uri)

        setContent {
            ExoPlayerSampleAppTheme {
                VideoPlayerScreen(
                    videoItem = videoItem,
                    playerManager = playerManager!!,
                    exoPlayer = exoPlayer!!,
                    onPlayerViewCreated = { view -> playerView = view },
                    onBackClick = { finish() },
                    onFullscreenToggle = { isFullscreen ->
                        if (isFullscreen) {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            hideSystemUI()
                        } else {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            showSystemUI()
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerView?.onResume()
    }

    override fun onResume() {
        super.onResume()
        playerManager?.play()
    }

    override fun onPause() {
        super.onPause()
        playerManager?.pause()
    }

    override fun onStop() {
        super.onStop()
        playerView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager?.release()
        playerManager = null
        exoPlayer = null
        playerView = null
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

@Composable
fun VideoPlayerScreen(
    videoItem: VideoItem,
    playerManager: ExoPlayerManager,
    exoPlayer: ExoPlayer,
    onPlayerViewCreated: (PlayerView) -> Unit,
    onBackClick: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit
) {
    val viewModel: VideoPlayerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.updatePlaying(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.updateLoading(true)
                    Player.STATE_READY -> {
                        viewModel.updateLoading(false)
                        viewModel.updatePosition(exoPlayer.currentPosition, exoPlayer.duration)
                    }
                    Player.STATE_ENDED -> {
                        viewModel.updatePlaying(false)
                        viewModel.updateLoading(false)
                    }
                    Player.STATE_IDLE -> viewModel.updateLoading(false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.updateError(error.message ?: "Playback error")
                viewModel.updateLoading(false)
            }
        }
        
        exoPlayer.addListener(listener)

        // Update position periodically (only when not dragging)
        while (true) {
            delay(100)
            val currentState = viewModel.uiState.value
            if (!currentState.isDragging) {
                // Update position for both playing and paused videos
                viewModel.updatePosition(exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.showControls()
                        coroutineScope.launch {
                            delay(3000)
                            viewModel.hideControls()
                        }
                    }
                )
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    onPlayerViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Error message
        uiState.error?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Controls overlay
        if (uiState.showControls) {
            LaunchedEffect(uiState.showControls) {
                if (uiState.showControls) {
                    delay(3000)
                    viewModel.hideControls()
                }
            }
            
            VideoPlayerControls(
                uiState = uiState,
                viewModel = viewModel,
                playerManager = playerManager,
                exoPlayer = exoPlayer,
                onBackClick = onBackClick,
                onFullscreenToggle = onFullscreenToggle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VideoPlayerControls(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    playerManager: ExoPlayerManager,
    exoPlayer: ExoPlayer,
    onBackClick: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showSpeedDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Top bar with back button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    viewModel.toggleFullscreen()
                    onFullscreenToggle(uiState.isFullscreen)
                }
            ) {
                Icon(
                    imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }

        // Center play/pause button
        IconButton(
            onClick = {
                if (uiState.isPlaying) {
                    playerManager.pause()
                } else {
                    playerManager.play()
                }
            },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Progress bar and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show seek position while dragging, otherwise show current position
                val displayPosition = if (uiState.isDragging) uiState.seekPosition else uiState.currentPosition
                // Slider value should follow drag position, otherwise use current position
                val sliderValue = if (uiState.isDragging) uiState.seekPosition.toFloat() else uiState.currentPosition.toFloat()
                
                Text(
                    text = formatTime(displayPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(60.dp)
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        // Update seek position while dragging (don't seek yet)
                        val seekPos = newValue.toLong().coerceIn(0L, uiState.duration)
                        if (!uiState.isDragging) {
                            // Initialize dragging state
                            viewModel.setDragging(true)
                            viewModel.setSeekPosition(seekPos)
                        } else {
                            // Update seek position while dragging
                            viewModel.setSeekPosition(seekPos)
                        }
                    },
                    onValueChangeFinished = {
                        // Seek to the final position when user releases
                        val finalPosition = uiState.seekPosition
                        viewModel.setDragging(false)
                        playerManager.seekTo(finalPosition)
                        viewModel.updatePosition(finalPosition, uiState.duration)
                    },
                    valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Text(
                    text = formatTime(uiState.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip backward
                IconButton(
                    onClick = {
                        playerManager.seekBackward(10_000L)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Skip backward 10s",
                        tint = Color.White
                    )
                }

                // Play/Pause
                IconButton(
                    onClick = {
                        if (uiState.isPlaying) {
                            playerManager.pause()
                        } else {
                            playerManager.play()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                // Skip forward
                IconButton(
                    onClick = {
                        playerManager.seekForward(10_000L)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Skip forward 10s",
                        tint = Color.White
                    )
                }

                // Speed control
                TextButton(
                    onClick = { showSpeedDialog = true }
                ) {
                    Text(
                        text = "${uiState.playbackSpeed}x",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    // Speed selection dialog
    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { speed ->
                playerManager.setPlaybackSpeed(speed)
                viewModel.updatePlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

@Composable
fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = { onSpeedSelected(speed) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${speed}x")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}

