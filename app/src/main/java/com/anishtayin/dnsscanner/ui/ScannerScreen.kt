package com.anishtayin.dnsscanner.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.anishtayin.dnsscanner.dns.fastestMeasuredMs
import com.anishtayin.dnsscanner.dns.latencyBarFraction
import com.anishtayin.dnsscanner.dns.rankResults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val importedIps by viewModel.importedIps.collectAsState()
    val customIps by viewModel.customIps.collectAsState()
    val importOutcome by viewModel.importOutcome.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var customIpInput by remember { mutableStateOf("") }
    var showClearImportedDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                withContext(Dispatchers.Main) {
                    if (text == null) viewModel.reportImportUnreadable()
                    else viewModel.importTxtContent(text)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { viewModel.reportImportUnreadable() }
            }
        }
    }

    val summary = viewModel.summary()
    val fastestMs = fastestMeasuredMs(results)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // سربرگ برند
            item { BrandHeader() }

            // دکمه‌ی اصلی اسکن + لغو
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.scanAll() },
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.scan_all),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.cancelScan() },
                        enabled = isScanning,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }

            // پیشرفت واقعی اسکن (فقط وقتی اسکن در جریان است)
            if (isScanning && progressTotal > 0) {
                item {
                    ScanProgressCard(done = progressDone, total = progressTotal)
                }
            }

            // خلاصه‌ی نتایج + نمودار تأخیر
            if (results.isNotEmpty()) {
                item {
                    SummaryCard(
                        summary = summary,
                        lastScanTime = lastScanTime,
                    )
                }
                val chartResults = rankResults(results).filter { it.correct && it.bestMs != null }.take(6)
                if (chartResults.isNotEmpty()) {
                    item {
                        LatencyChartCard(results = chartResults, fastestMs = fastestMs)
                    }
                }
            }

            // دکمه‌های مدیریت سرورها
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.add_custom))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("text/plain")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.import_txt))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (importedIps.isNotEmpty()) {
                        TextButton(onClick = { showClearImportedDialog = true }) {
                            Text(stringResource(R.string.clear_imported, importedIps.size))
                        }
                    }
                    if (customIps.isNotEmpty() || importedIps.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.custom_count,
                                customIps.size + importedIps.size,
                                importedIps.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // فهرست نتایج
            if (results.isEmpty() && !isScanning) {
                item {
                    Text(
                        text = stringResource(R.string.no_results_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.results_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(results, key = { it.resolver.ip }) { result ->
                    ResolverCard(
                        result = result,
                        fastestMs = fastestMs,
                        onRemoveCustom = if (result.resolver.custom) {
                            { viewModel.removeCustomIp(result.resolver.ip) }
                        } else {
                            null
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.honesty_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // دیالوگ افزودن سرور دستی
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.txt_format_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

    // تأیید پاک کردن سرورهای واردشده
    if (showClearImportedDialog) {
        AlertDialog(
            onDismissRequest = { showClearImportedDialog = false },
            title = { Text(stringResource(R.string.clear_imported_title)) },
            text = { Text(stringResource(R.string.clear_imported_confirm, importedIps.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImported()
                    showClearImportedDialog = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearImportedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // نتیجه‌ی ایمپورت فایل TXT (خلاصه‌ی صادقانه یا پیام خطا)
    importOutcome?.let { outcome ->
        ImportOutcomeDialog(
            outcome = outcome,
            onDismiss = { viewModel.clearImportOutcome() },
        )
    }
}

/** سربرگ برند: گرادیان + گرافیک رادار + معرفی برنامه. */
@Composable
private fun BrandHeader() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadarGraphic(modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/** گرافیک رادار: دایره‌های هم‌مرکز و عقربه — فقط تزئینی، عددی نشان نمی‌دهد. */
@Composable
private fun RadarGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val ring = Color.White.copy(alpha = 0.85f)
        val faintRing = Color.White.copy(alpha = 0.45f)
        drawCircle(color = faintRing, radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawCircle(color = ring, radius = radius * 0.62f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawCircle(color = faintRing, radius = radius * 0.28f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        // عقربه
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(center.x + radius * 0.62f, center.y - radius * 0.62f),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color(0xFFFFC107), radius = radius * 0.14f, center = center)
        drawCircle(
            color = Color.White,
            radius = radius * 0.1f,
            center = Offset(center.x + radius * 0.62f, center.y - radius * 0.62f),
        )
    }
}

/** پیشرفت اسکن: درصد و شمارنده از شمار واقعی سرورهای تمام‌شده می‌آید، نه انیمیشن ساختگی. */
@Composable
private fun ScanProgressCard(done: Int, total: Int) {
    val target = if (total > 0) done.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "scanProgress",
    )
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.scanning_progress, done, total),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "%${(target * 100).toInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** کارت خلاصه: سریع‌ترین سرور، میانگین تأخیر، تعداد سالم‌ها و زمان آخرین اسکن. */
@Composable
private fun SummaryCard(
    summary: com.anishtayin.dnsscanner.dns.ResultsSummary,
    lastScanTime: Long?,
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryStat(
                    label = stringResource(R.string.summary_fastest),
                    value = summary.fastest?.let { "${it.bestMs} ms" } ?: "—",
                    sub = summary.fastest?.let { "${it.resolver.provider} ${it.resolver.ip}" } ?: "",
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = stringResource(R.string.summary_average),
                    value = summary.averageMs?.let { "$it ms" } ?: "—",
                    sub = stringResource(R.string.summary_average_sub),
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = stringResource(R.string.summary_working),
                    value = "${summary.working} / ${summary.total}",
                    sub = stringResource(R.string.summary_working_sub),
                    modifier = Modifier.weight(1f),
                )
            }
            lastScanTime?.let { time ->
                Spacer(modifier = Modifier.height(8.dp))
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
                Text(
                    text = stringResource(R.string.last_scan, formatted, summary.working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** نمودار میله‌ای تأخیر: تا ۶ سرور سالمِ اول، میله نسبت به سریع‌ترین سرور. */
@Composable
private fun LatencyChartCard(results: List<ScanResult>, fastestMs: Long?) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.chart_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chart_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            results.forEach { result ->
                val fraction = latencyBarFraction(result.bestMs, fastestMs)
                val animated by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(durationMillis = 500),
                    label = "chartBar",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = result.resolver.provider,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(86.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animated)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(latencyBarColor(result.status)),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${result.bestMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(64.dp),
                        textAlign = TextAlign.End,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ResolverCard(
    result: ScanResult,
    fastestMs: Long?,
    onRemoveCustom: (() -> Unit)?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fraction = latencyBarFraction(result.bestMs, fastestMs)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500),
        label = "cardBar",
    )
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${result.resolver.provider} — ${result.resolver.ip}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(result.status)
            }
            if (result.resolver.custom) {
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (result.resolver.imported) stringResource(R.string.badge_imported)
                            else stringResource(R.string.badge_custom),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
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
                Spacer(modifier = Modifier.height(6.dp))
                // میله‌ی تأخیر نسبت به سریع‌ترین سرور
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(latencyBarColor(result.status)),
                    )
                }
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
    val colors = statusColors(status)
    Surface(
        color = colors.container,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            color = colors.onContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ProtocolChip(name: String, latencyMs: Long?) {
    AssistChip(
        onClick = {},
        label = { Text(if (latencyMs != null) "$name: $latencyMs ms" else "$name: —") },
    )
}

/** دیالوگ نتیجه‌ی ایمپورت: تعداد اضافه‌شده / تکراری / نامعتبر + نمونه‌های نامعتبر. */
@Composable
private fun ImportOutcomeDialog(outcome: TxtImportOutcome, onDismiss: () -> Unit) {
    when (outcome) {
        TxtImportOutcome.EmptyFile -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_empty_title)) },
            text = { Text(stringResource(R.string.import_empty)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            },
        )
        TxtImportOutcome.UnreadableFile -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(stringResource(R.string.import_unreadable)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            },
        )
        is TxtImportOutcome.Completed -> {
            val result = outcome.result
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_result_title)) },
                text = {
                    Column {
                        if (result.added.isEmpty()) {
                            Text(
                                text = stringResource(R.string.import_no_valid),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = stringResource(
                                R.string.import_summary,
                                result.added.size,
                                result.duplicatesSkipped,
                                result.invalidCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (result.invalidExamples.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.import_invalid_examples),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            result.invalidExamples.forEach { example ->
                                Text(
                                    text = "• $example",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
                },
            )
        }
    }
}
