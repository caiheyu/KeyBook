package com.github.caiheyu.keybook

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.github.caiheyu.keybook.data.settings.ThemeMode
import com.github.caiheyu.keybook.ui.KeyBookApp
import com.github.caiheyu.keybook.ui.KeyBookViewModel
import com.github.caiheyu.keybook.ui.theme.KeyBookTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Vault fields and file passwords must never be offered to or saved by Autofill.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        enableEdgeToEdge()
        setContent {
            val viewModel: KeyBookViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.FOLLOW_SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KeyBookTheme(darkTheme = dark) {
                KeyBookApp(viewModel)
            }
        }
    }
}
