package com.anishtayin.dnsscanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.anishtayin.dnsscanner.ui.ScannerScreen
import com.anishtayin.dnsscanner.ui.ScannerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DnsScannerTheme {
                ScannerScreen(
                    viewModel = viewModel,
                    onShare = { text, title -> shareText(text, title) },
                )
            }
        }
    }

    private fun shareText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startActivity(Intent.createChooser(intent, title))
    }
}

@Composable
fun DnsScannerTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
