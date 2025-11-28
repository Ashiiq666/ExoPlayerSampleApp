package com.aq.exoplayersampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aq.exoplayersampleapp.ui.theme.ExoPlayerSampleAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExoPlayerSampleAppTheme {
                Surface(color = Color.Black) {
                    val viewModel: VideoPlayerViewModel = viewModel()
                    VideoPlayerScreen(
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}