package com.aq.exoplayersampleapp

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.VideoSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
    private var networkReceiver: BroadcastReceiver? = null

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
        
        // Check for low memory device and adjust quality
        playerManager!!.adjustQualityForLowMemory()
        
        playerManager!!.loadVideo(videoItem.uri)
        
        // Register network change receiver for adaptive quality adjustment
        registerNetworkReceiver()

        setContent {
            ExoPlayerSampleAppTheme {
                VideoPlayerScreen(
                    videoItem = videoItem,
                    playerManager = playerManager!!,
                    exoPlayer = exoPlayer!!,
                    activity = this@VideoPlayerActivity,
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
        networkReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
        networkReceiver = null
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
    
    private fun registerNetworkReceiver() {
        networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Update quality when network changes
                playerManager?.updateNetworkQuality()
            }
        }
        
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, filter)
    }
}

@Composable
fun VideoPlayerScreen(
    videoItem: VideoItem,
    playerManager: ExoPlayerManager,
    exoPlayer: ExoPlayer,
    activity: ComponentActivity,
    onPlayerViewCreated: (PlayerView) -> Unit,
    onBackClick: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit
) {
    // Get video title from videoItem
    val videoTitle = videoItem.displayName
    val viewModel: VideoPlayerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(exoPlayer) {
        // Setup quality change listener
        playerManager.setQualityChangeListener { videoSize ->
            val quality = formatVideoQuality(videoSize.width, videoSize.height)
            viewModel.updateVideoQuality(quality)
        }
        
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
                        // Update initial quality
                        val videoSize = exoPlayer.videoSize
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val quality = formatVideoQuality(videoSize.width, videoSize.height)
                            viewModel.updateVideoQuality(quality)
                        }
                    }
                    Player.STATE_ENDED -> {
                        viewModel.updatePlaying(false)
                        viewModel.updateLoading(false)
                    }
                    Player.STATE_IDLE -> viewModel.updateLoading(false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMessage = when {
                    error.cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException -> {
                        val httpError = error.cause as? androidx.media3.datasource.HttpDataSource.HttpDataSourceException
                        val responseCode = try {
                            (httpError as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                        } catch (e: Exception) {
                            null
                        }
                        when (responseCode) {
                            404 -> "Video not found (404). Please check the URL."
                            403 -> "Access denied (403). The video may be restricted."
                            401 -> "Unauthorized (401). Authentication required."
                            else -> "Network error: ${responseCode ?: "Unknown"}"
                        }
                    }
                    error.cause is java.net.UnknownHostException -> {
                        "Cannot connect to server. Check your internet connection."
                    }
                    error.cause is java.net.SocketTimeoutException -> {
                        "Connection timeout. The server took too long to respond."
                    }
                    error.message?.contains("404") == true -> {
                        "Video not found (404). The URL may be incorrect or the video was removed."
                    }
                    else -> {
                        "Playback error: ${error.message ?: "Unknown error"}"
                    }
                }
                viewModel.updateError(errorMessage)
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
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updateError(null)
                        // Retry loading the video
                        playerManager.loadVideo(videoItem.uri)
                    }
                ) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onBackClick() }) {
                    Text("Go Back")
                }
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
                videoTitle = videoTitle,
                activity = activity,
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
    videoTitle: String,
    activity: ComponentActivity,
    onBackClick: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    
    // AudioManager for volume control
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        // Top bar with back button, title, cast, and profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

            // Title in center
            Text(
                text = videoTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Cast and Profile icons
            Row {
                IconButton(onClick = { 
                    showCastDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { 
                    Toast.makeText(context, "Profile settings", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom controls - Netflix style layout
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Upper controls row: Picture-in-picture, Speed, Speaker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Picture-in-picture icon (left)
                IconButton(onClick = { 
                    enterPictureInPictureMode(activity)
                }) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in Picture",
                        tint = Color.White
                    )
                }

                // Speed indicator (center) - shows quality if available
                TextButton(
                    onClick = { showSpeedDialog = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${uiState.playbackSpeed.toInt()}x",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.currentVideoQuality.isNotEmpty()) {
                            Text(
                                text = uiState.currentVideoQuality,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Speaker icon (right) - shows mute/unmute and volume control
                IconButton(onClick = { 
                    showVolumeDialog = true
                }) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar with red color (Netflix style)
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
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(50.dp)
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
                        thumbColor = Color(0xFFE50914), // Red thumb (Netflix red)
                        activeTrackColor = Color(0xFFE50914), // Red progress bar
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Text(
                    text = formatTime(uiState.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lower controls row: Shuffle, Previous, Pause, Next, Loop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (left)
                IconButton(onClick = { 
                    viewModel.toggleShuffle()
                    Toast.makeText(context, 
                        if (uiState.isShuffleEnabled) "Shuffle off" else "Shuffle on", 
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.isShuffleEnabled) Color(0xFFE50914) else Color.White
                    )
                }

                // Previous/Previous track
                IconButton(
                    onClick = {
                        playerManager.seekBackward(10_000L)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }

                // Play/Pause (center)
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
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next/Next track
                IconButton(
                    onClick = {
                        playerManager.seekForward(10_000L)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }

                // Loop/Repeat (right)
                IconButton(onClick = { 
                    viewModel.toggleRepeat()
                    val newRepeatMode = (uiState.repeatMode + 1) % 3
                    val repeatText = when (newRepeatMode) {
                        0 -> "Repeat off"
                        1 -> "Repeat one"
                        else -> "Repeat all"
                    }
                    Toast.makeText(context, repeatText, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (uiState.repeatMode > 0) Color(0xFFE50914) else Color.White
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

    // Volume control dialog
    if (showVolumeDialog) {
        VolumeControlDialog(
            currentVolume = uiState.volume,
            isMuted = uiState.isMuted,
            audioManager = audioManager,
            onVolumeChange = { volume ->
                setDeviceVolume(audioManager, volume)
                viewModel.setVolume(volume)
            },
            onMuteToggle = {
                toggleDeviceMute(audioManager, viewModel)
            },
            onDismiss = { showVolumeDialog = false }
        )
    }

    // Cast dialog
    if (showCastDialog) {
        CastDialog(
            onDismiss = { showCastDialog = false },
            onCastSelected = { deviceName ->
                Toast.makeText(context, "Casting to $deviceName", Toast.LENGTH_SHORT).show()
                showCastDialog = false
            }
        )
    }
}

// Helper function to enter Picture-in-Picture mode
private fun enterPictureInPictureMode(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(activity, "Picture-in-Picture not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, "Picture-in-Picture not available", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(activity, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
    }
}

// Helper function to set device volume
private fun setDeviceVolume(audioManager: AudioManager, volume: Int) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val volumeLevel = (maxVolume * volume / 100).coerceIn(0, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel, 0)
}

// Helper function to toggle mute
private fun toggleDeviceMute(audioManager: AudioManager, viewModel: VideoPlayerViewModel) {
    viewModel.toggleMute()
    if (viewModel.uiState.value.isMuted) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    } else {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = viewModel.uiState.value.volume
        val volumeLevel = (maxVolume * volume / 100).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel, 0)
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

@Composable
fun VolumeControlDialog(
    currentVolume: Int,
    isMuted: Boolean,
    audioManager: AudioManager,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volume Control") },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Mute toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMuteToggle() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (isMuted) "Unmute" else "Mute")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Volume slider
                Text("Volume: $currentVolume%")
                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = !isMuted,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CastDialog(
    onDismiss: () -> Unit,
    onCastSelected: (String) -> Unit
) {
    // Simulated cast devices list
    val castDevices = listOf(
        "Living Room TV",
        "Bedroom Chromecast",
        "Kitchen Display"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cast to Device") },
        text = {
            Column {
                if (castDevices.isEmpty()) {
                    Text("No devices found")
                } else {
                    castDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCastSelected(device) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(device)
                        }
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

fun formatVideoQuality(width: Int, height: Int): String {
    return when {
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        width >= 640 || height >= 360 -> "360p"
        else -> "240p"
    }
}

