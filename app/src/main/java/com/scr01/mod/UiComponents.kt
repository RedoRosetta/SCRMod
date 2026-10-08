package com.scr01.mod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DiagnosticLogCard(logs: List<String>, context: Context) {
    var showFullLogs by remember { mutableStateOf(false) }
    val preview = logs.takeLast(5)
    ScrModCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("诊断记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                ScrModTextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SCRMod 日志", logs.joinToString("\n")))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制")
                }
            }
            if (preview.isEmpty()) {
                Text("暂无诊断记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                    preview.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (logs.size > preview.size) {
                ScrModTextButton(onClick = { showFullLogs = true }) {
                Text("查看全部记录")
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (showFullLogs) {
        FullLogsDialog(logs = logs, onDismiss = { showFullLogs = false })
    }
}

@Composable
internal fun FullLogsDialog(logs: List<String>, onDismiss: () -> Unit) {
    ScrModDialog("全部诊断记录", onDismiss,
        actions = { ScrModTextButton(onClick = onDismiss) { Text("关闭") } }) {
        if (logs.isEmpty()) Text("暂无诊断记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else logs.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
internal fun PreferenceRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatusMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StatusLight(text: String, ready: Boolean, modifier: Modifier = Modifier) {
    StatusLight(text, if (ready) UiStatus.SUCCESS else UiStatus.UNKNOWN, modifier)
}

@Composable
internal fun StatusLight(text: String, status: UiStatus, modifier: Modifier = Modifier) {
    Row(modifier.semantics { stateDescription = text }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
        Box(Modifier.size(7.dp).background(statusColor(status), CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "未知"

internal fun appHealthLabel(status: DeviceStatus, moduleAsset: ModuleAssetStatus): String = when {
    status.root == CheckState.Checking ||
        status.device == CheckState.Checking ||
        status.firmware == CheckState.Checking ||
        !moduleAsset.checked -> "正在检查"
    status.root == CheckState.Passed &&
        status.device == CheckState.Passed &&
        status.firmware == CheckState.Passed &&
        moduleAsset.safetyGatePassed -> "检查通过"
    else -> "功能受限"
}

internal fun rootStatusLabel(state: CheckState): String = when (state) {
    CheckState.Checking -> "检测中"
    CheckState.Passed -> "已授权"
    CheckState.Failed -> "未授权"
}

internal fun isUserFacingLog(message: String): Boolean {
    val hiddenTokens = listOf(
        "命令：",
        "stdout：",
        "stderr：",
        "Root 入口：",
        "IDC",
        "patch",
        "冻结",
        "门禁",
        "内核",
        "hostapd_cli",
        "swlan0",
        "scr01_idc36",
    )
    return message.isNotBlank() && hiddenTokens.none { message.contains(it, ignoreCase = true) }
}

internal fun currentHotspotBandLabel(hotspot: HotspotStatus): String = when {
    hotspot.state != HotspotState.On -> "未运行"
    (hotspot.frequencyMhz ?: 0) in 2400..2499 -> "2.4 GHz"
    (hotspot.frequencyMhz ?: 0) >= 5000 -> "5 GHz"
    else -> "未知"
}

@Composable
internal fun StatusRow(label: String, value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 480.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceLg),
            verticalAlignment = Alignment.Top) {
            val labelModifier = if (wide) Modifier.width(200.dp) else Modifier.weight(1f)
            Text(label, modifier = labelModifier, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun DataUsageMaxOverrideCard(
    period: DataUsagePeriod,
    onPeriodChanged: (DataUsagePeriod) -> Unit,
    config: DataUsageMaxOverrideConfig,
    input: String,
    actualValue: String?,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onInputChanged: (String) -> Unit,
    onApply: () -> Unit,
    onDisable: () -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    val currentText = actualValue?.let { "当前 ${formatDataUsageGb(it)} GB" } ?: "当前未知"
    val body = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
    val label = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    val title = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
    ScrModCard(Modifier.fillMaxWidth(), shape = AppUi.CardShape) {
        Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale.coerceAtLeast(1f)
                val wide = maxWidth >= (600 * fontScale).dp
                val picker: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
                        DataUsagePeriod.entries.forEach { item ->
                            ScrModChoice(selected = period == item, enabled = !busy, onClick = { onPeriodChanged(item) },
                                label = { Text(item.label, style = label) })
                        }
                    }
                }
                val field: @Composable () -> Unit = {
                    ScrModTextField(value = input, onValueChange = onInputChanged, enabled = !busy, singleLine = true,                         placeholder = { Text("上限", style = body) }, suffix = { Text("GB", style = label) }, textStyle = body,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(if (wide) 96.dp else 128.dp))
                }
                val apply: @Composable () -> Unit = {
                    ScrModButton(onClick = onApply, enabled = !busy && !config.recoveryPending && DataUsageMaxValidator.parseTarget(input).isSuccess,
                        contentPadding = AppUi.ButtonPadding) { Text(if (busy) "处理中…" else "设置上限", style = label) }
                }
                val reset: @Composable () -> Unit = {
                    ScrModTextButton(onClick = onRestoreDefaults, enabled = !busy, contentPadding = PaddingValues(horizontal = AppUi.SpaceSm, vertical = AppUi.SpaceSm)) {
                        Text("恢复默认", style = label)
                    }
                }
                if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                    Text("数据上限", style = title)
                    picker()
                    Text(currentText, style = body)
                    field()
                    apply()
                    Spacer(Modifier.weight(1f))
                    reset()
                } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("数据上限", Modifier.weight(1f), style = title); reset()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        picker(); Text(currentText, style = body)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        field(); apply()
                    }
                }
            }
            if (config.enabled || config.recoveryPending) {
                ScrModTextButton(onClick = onDisable, enabled = !busy, contentPadding = PaddingValues(horizontal = AppUi.SpaceSm, vertical = AppUi.SpaceSm)) { Text("恢复原值", style = label) }
            }
            if (!busy && !config.recoveryPending && DataUsageMaxValidator.parseTarget(input).isFailure) {
                Text("请输入 1～2000 GB 的整数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let {
                Text(it, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
internal fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = AppUi.FieldHeight).padding(vertical = AppUi.SpaceSm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScrModSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
