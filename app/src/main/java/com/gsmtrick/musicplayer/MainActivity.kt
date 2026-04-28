package com.gsmtrick.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gsmtrick.musicplayer.ui.AppRoot
import com.gsmtrick.musicplayer.ui.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.connectController(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.releaseController()
    }
}
