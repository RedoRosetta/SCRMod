package com.scr01.mod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ChannelOption(val label: String, val spec: ChannelSpec? = null) {
    val channel: Int? get() = spec?.channel
    val frequency: Int? get() = spec?.frequencyMhz
}

private val channels = listOf(ChannelOption("自动（推荐）")) + SupportedChannels.all.map { spec ->
    ChannelOption("${spec.channel}（${spec.frequencyMhz} MHz）", spec)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HotspotPage(
    status: DeviceStatus,
    hotspot: HotspotStatus,
    moduleAsset: ModuleAssetStatus,
    autoSettings: AutoChannelSettings,
    modifier: Modifier = Modifier,
    addLog: (String) -> Unit,
    onRefresh: () -> Unit,
    onAutoSettingsChanged: (AutoChannelSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(autoSettings.channel) {
        mutableStateOf(channels.firstOrNull { it.channel == autoSettings.channel } ?: channels.first())
    }
    var expanded by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf(UiOperationState()) }
    val isApplying = operation.running
    var showApplyConfirm by remember { mutableStateOf(false) }
    var isOpeningBandSettings by remember { mutableStateOf(false) }
    var bandSettingsMessage by remember { mutableStateOf<String?>(null) }
    var bandSettingsMessageIsError by remember { mutableStateOf(false) }
    val isBusy = isOpeningBandSettings || isApplying
    val safeToModify = status.root == CheckState.Passed &&
        status.device == CheckState.Passed &&
        status.firmware == CheckState.Passed &&
        moduleAsset.safetyGatePassed
    val bandLabel = currentHotspotBandLabel(hotspot)
    val isFiveGhz = bandLabel == "5 GHz"
    val canApply = safeToModify && !isBusy && hotspot.state == HotspotState.On && isFiveGhz

    fun openBandSettings() {
        if (isBusy) return
        isOpeningBandSettings = true
        bandSettingsMessage = null
        bandSettingsMessageIsError = false
        scope.launch {
            val result = SamsungWifiSettingsLauncher(RootCommandExecutor(addLog)).open()
            bandSettingsMessage = result.message
            bandSettingsMessageIsError = !result.success
            isOpeningBandSettings = false
        }
    }

    ScrModPage(modifier) {
        ScrModCard(Modifier.fillMaxWidth(), shape = AppUi.CardShape) {
            Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                Text("热点状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val signal: @Composable () -> Unit = { StatusLight(hotspot.state.label(), hotspot.state == HotspotState.On) }
                    val bandStatus: @Composable () -> Unit = { HotspotMetric(Icons.Default.Wifi, bandLabel) }
                    val channelStatus: @Composable () -> Unit = { HotspotMetric(Icons.Default.SettingsInputAntenna, listOfNotNull(hotspot.channel?.toString(), hotspot.frequencyMhz?.let { "$it MHz" }).joinToString(" · ").ifBlank { "未知" }) }
                    val bandwidthStatus: @Composable () -> Unit = { HotspotMetric(Icons.Default.SwapHoriz, hotspot.bandwidthMhz?.let { "$it MHz" } ?: "未知") }
                    if (useWideLayout(maxWidth)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceLg)) {
                        signal(); bandStatus(); channelStatus(); bandwidthStatus()
                    } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                            Box(Modifier.weight(1f)) { signal() }; Box(Modifier.weight(1f)) { bandStatus() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                            Box(Modifier.weight(1f)) { channelStatus() }; Box(Modifier.weight(1f)) { bandwidthStatus() }
                        }
                    }
                }
            }
        }

        ScrModCard(Modifier.fillMaxWidth(), shape = AppUi.CardShape) {
            Column(Modifier.padding(AppUi.SectionPadding)) {
                Text("热点设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(AppUi.SpaceSm))
                Text("待应用的 5 GHz 信道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(AppUi.SpaceSm))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!isBusy && isFiveGhz) expanded = it },
                        modifier = fieldModifier,
                    ) {
                        ScrModTextField(
                            value = if (isFiveGhz) selected.channel?.let { "$it · ${selected.frequency} MHz" } ?: "自动" else "切换到 5 GHz 后设置",
                            onValueChange = {},
                            readOnly = true,
                            
                                                        singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            enabled = !isBusy && isFiveGhz,
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
                            channels.forEach { option ->
                                DropdownMenuItem(
                                    enabled = !isBusy,
                                    text = { Text(option.label) },
                                    onClick = {
                                        selected = option
                                        expanded = false
                                        option.channel?.let { channel ->
                                            onAutoSettingsChanged(autoSettings.copy(channel = channel))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    }
                    val band: @Composable () -> Unit = {
                        ScrModSecondaryButton(onClick = ::openBandSettings, enabled = !isBusy,
                            contentPadding = AppUi.ButtonPadding, shape = AppUi.ControlShape) {
                            Text(bandLabel); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                        }
                    }
                    val apply: @Composable () -> Unit = {
                    ScrModButton(
                        onClick = { showApplyConfirm = true },
                        enabled = canApply,
                    ) {
                        Text(if (isApplying) "应用中…" else "应用")
                    }
                    }
                    val wide = useWideLayout(maxWidth)
                    if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                        band(); field(Modifier.weight(1f)); apply()
                    } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        field(Modifier.fillMaxWidth())
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm),
                            verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) { band(); apply() }
                    }

                }
                bandSettingsMessage?.takeIf { bandSettingsMessageIsError }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!canApply) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            isBusy -> "正在处理，请稍候"
                            status.root != CheckState.Passed -> "请先完成 Root 授权"
                            !safeToModify -> "当前设备检查未通过"
                            hotspot.state != HotspotState.On -> "请先开启热点"
                            !isFiveGhz -> "请在频段设置中选择 5 GHz"
                            else -> "暂时无法应用信道"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                operation.message.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = if (operation.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch(
                    title = "开机自动应用信道",
                    subtitle = if (autoSettings.applyAfterBoot) "设备开机后应用已保存的信道" else "请先在设置页开启“开机自动应用设置”",
                    checked = autoSettings.applyOnBoot,
                    enabled = autoSettings.applyAfterBoot && !isBusy,
                    onCheckedChange = { enabled -> onAutoSettingsChanged(autoSettings.copy(applyOnBoot = enabled)) },
                )
            }
        }
        NatDiagnosticsCard(addLog)
    }

    if (showApplyConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("应用信道设置？") },
            text = { Text(selected.channel?.let { "热点将切换到信道 $it。" } ?: "将停止固定信道控制，交由系统管理。") },
            confirmButton = {
                ScrModTextButton(onClick = {
                    showApplyConfirm = false
                    operation = UiOperationState(UiOperationPhase.RUNNING, "正在应用信道")
                    scope.launch {
                        try {
                        val controller = ChannelController(context, RootCommandExecutor {}, {})
                        val result = controller.apply(selected.channel, status, moduleAsset, hotspot)
                        val message = if (result.success) {
                            selected.channel?.let { "已应用信道 $it" } ?: "已交由系统管理信道"
                        } else if (result.rollback?.state == KernelRollbackState.ROLLBACK_FAILED) {
                            "应用失败，未确认恢复到安全状态；请查看诊断日志"
                        } else if (result.kernelVerification == KernelVerificationState.VERIFICATION_UNAVAILABLE ||
                            result.kernelVerification == KernelVerificationState.VERIFICATION_FAILED
                        ) {
                            "安全验证未通过，未切换信道；请查看诊断日志"
                        } else {
                            "应用失败，请在设置页查看诊断日志"
                        }
                        operation = UiOperationState(
                            if (result.success) UiOperationPhase.SUCCEEDED else if (result.rollback?.state == KernelRollbackState.ROLLBACK_FAILED || result.kernelVerification == KernelVerificationState.VERIFICATION_UNAVAILABLE) UiOperationPhase.UNKNOWN else UiOperationPhase.FAILED,
                            message,
                        )
                        addLog(if (result.success) "信道设置已更新" else "信道设置应用失败")
                        onRefresh()
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            operation = UiOperationState(UiOperationPhase.UNKNOWN, "操作未确认，请刷新当前热点状态")
                            addLog("信道操作异常：${error.javaClass.simpleName}")
                        } finally {
                            if (operation.running) operation = UiOperationState(UiOperationPhase.UNKNOWN, "操作中断，请刷新确认")
                        }
                    }
                }) { Text("确认应用") }
            },
            dismissButton = { ScrModTextButton(onClick = { showApplyConfirm = false }) { Text("取消") } },
        )
    }
}


@Composable
private fun HotspotMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
