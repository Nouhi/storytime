package com.storytime.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.storytime.app.ui.navigation.AppNavigation
import com.storytime.app.ui.theme.StorytimeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore saved language on startup
        val app = application as StorytimeApp
        lifecycleScope.launch {
            val lang = app.preferencesManager.language.first()
            if (lang != "en") {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(lang)
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            StorytimeTheme {
                AppNavigation()
            }
        }
    }
}
