package com.gsmtrick.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.gsmtrick.musicplayer.ui.AppRoot
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            viewModel.prefs
                .distinctUntilChangedBy { it.language }
                .onEach { p -> applyLocale(p.language) }
                .collect {}
        }
        setContent {
            AppRoot(viewModel = viewModel)
        }
    }

    private fun applyLocale(language: String) {
        val tags = when (language) {
            "bn" -> "bn"
            "en" -> "en"
            else -> "" // system default
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
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
