package com.anishtayin.dnsscanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.anishtayin.dnsscanner.ui.DnsScannerTheme
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
