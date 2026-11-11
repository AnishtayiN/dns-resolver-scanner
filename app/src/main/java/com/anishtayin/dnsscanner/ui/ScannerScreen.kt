package com.anishtayin.dnsscanner.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anishtayin.dnsscanner.R
import com.anishtayin.dnsscanner.dns.ResolverStatus
import com.anishtayin.dnsscanner.dns.ScanResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onShare: (text: String, title: String) -> Unit,
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val progressDone by viewModel.progressDone.collectAsState()
    val progressTotal by viewModel.progressTotal.collectAsState()
    val results by viewModel.results.collectAsState()
    val lastScanTime by viewModel.lastScanTime.collectAsState()
    val invalidIp by viewModel.invalidIpAttempt.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var customIpInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.scanAll() },
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.scan_all))
                }
                OutlinedButton(
                    onClick = { viewModel.cancelScan() },
                    enabled = isScanning,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.add_custom))
                }
                OutlinedButton(
                    onClick = { onShare(viewModel.exportText(), "DNS Scanner TXT") },
                    enabled = results.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.export_txt))
                }
                OutlinedButton(
                    onClick = { onShare(viewModel.exportJson(), "DNS Scanner JSON") },
                    enabled = results.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.export_json))
                }
            }

            if (isScanning && progressTotal > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progressDone.toFloat() / progressTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.scanning_progress, progressDone, progressTotal),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            lastScanTime?.let { time ->
                Spacer(modifier = Modifier.height(8.dp))
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
                Text(
                    text = stringResource(R.string.last_scan, formatted, viewModel.workingCount()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (results.isEmpty() && !isScanning) {
                Text(
                    text = stringResource(R.string.no_results_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.resolver.ip }) { result ->
                    ResolverCard(
                        result = result,
                        onRemoveCustom = if (result.resolver.custom) {
                            { viewModel.removeCustomIp(result.resolver.ip) }
                        } else {
                            null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.honesty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                viewModel.clearInvalidIpFlag()
            },
            title = { Text(stringResource(R.string.add_custom)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = customIpInput,
                        onValueChange = { customIpInput = it },
                        label = { Text(stringResource(R.string.custom_ip_hint)) },
                        placeholder = { Text("1.1.1.1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (invalidIp) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.invalid_ip),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.addCustomIp(customIpInput)) {
                        customIpInput = ""
                        showAddDialog = false
                    }
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    viewModel.clearInvalidIpFlag()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ResolverCard(result: ScanResult, onRemoveCustom: (() -> Unit)?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(result.resolver.ip))
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${result.resolver.provider} — ${result.resolver.ip}",
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusChip(result.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (result.bestMs != null) {
                Text(
                    text = stringResource(
                        R.string.best_latency,
                        result.bestMs.toString(),
                        result.bestProtocol ?: "",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.no_latency),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProtocolChip("UDP", result.udpMs)
                ProtocolChip("TCP", result.tcpMs)
                ProtocolChip("DoT", result.dotMs)
                ProtocolChip("DoH", result.dohMs)
            }
            Text(
                text = stringResource(R.string.tap_to_copy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRemoveCustom != null) {
                TextButton(onClick = onRemoveCustom) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ResolverStatus) {
    val text = when (status) {
        ResolverStatus.WORKING -> stringResource(R.string.status_working)
        ResolverStatus.SLOW -> stringResource(R.string.status_slow)
        ResolverStatus.NO_ANSWER -> stringResource(R.string.status_no_answer)
        ResolverStatus.WRONG_ANSWER -> stringResource(R.string.status_wrong_answer)
    }
    val color = when (status) {
        ResolverStatus.WORKING -> MaterialTheme.colorScheme.primaryContainer
        ResolverStatus.SLOW -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = color),
    )
}

@Composable
private fun ProtocolChip(name: String, latencyMs: Long?) {
    AssistChip(
        onClick = {},
        label = { Text(if (latencyMs != null) "$name: $latencyMs ms" else "$name: —") },
    )
}
