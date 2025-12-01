package com.aq.exoplayersampleapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aq.exoplayersampleapp.ui.theme.ExoPlayerSampleAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExoPlayerSampleAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: VideoGalleryViewModel = viewModel()
                    
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    
                    val permissionsState = rememberMultiplePermissionsState(permissions)

                    // Request permissions on first launch if not granted
                    LaunchedEffect(Unit) {
                        if (!permissionsState.allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }

                    VideoGalleryScreen(
                        viewModel = viewModel,
                        hasPermissions = permissionsState.allPermissionsGranted,
                        onVideoClick = { videoItem ->
                            val intent = Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                                putExtra("video", videoItem)
                            }
                            startActivity(intent)
                        },
                        onTestUrlClick = { url ->
                            // Create a test VideoItem from URL
                            val testVideoItem = VideoItem(
                                id = -1,
                                uri = android.net.Uri.parse(url),
                                displayName = "Test Stream",
                                duration = 0,
                                size = 0,
                                dateAdded = System.currentTimeMillis()
                            )
                            val intent = Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                                putExtra("video", testVideoItem)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}