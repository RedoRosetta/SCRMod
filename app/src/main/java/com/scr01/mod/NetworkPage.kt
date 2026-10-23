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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val selectedBandwidth = autoSettings.targetBandwidthMhz
    val hotspotRestoreMessage by HotspotStartRestore.message.collectAsStateWithLifecycle()
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
        PageHeading("热点状态") {
            StatusLight(hotspot.state.label(),hotspot.state==HotspotState.On)
        }
        ScrModCard(Modifier.fillMaxWidth().heightIn(min=AppUi.StatusCardHeight), shape=AppUi.CardShape) {
            BoxWithConstraints(Modifier.padding(AppUi.SectionPadding)) {
                val values=listOf(Triple(Icons.Default.Wifi,"频率",hotspot.frequencyMhz?.let { "$it MHz" } ?: bandLabel),
                    Triple(Icons.Default.SettingsInputAntenna,"信道",hotspot.channel?.toString() ?: "待确认"),
                    Triple(Icons.Default.SwapHoriz,"频宽",hotspot.bandwidthMhz?.let { "$it MHz" } ?: "待确认"))
                if(useWideLayout(maxWidth)) Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
                    values.forEach { (icon,label,value)->Row(Modifier.weight(1f),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(AppUi.SpaceSm))
                        Text(label,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(AppUi.SpaceMd))
                        Text(value,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,maxLines=1)
                    } }
                } else Column(Modifier.fillMaxWidth()) {
                    values.forEach { (icon,label,value)->Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,maxLines=1)
                    } }
                }
            }
        }

        ScrModCard(Modifier.fillMaxWidth(), shape = AppUi.CardShape) {
            Column(Modifier.padding(start=AppUi.SpaceLg,end=AppUi.SpaceLg,top=AppUi.SpaceSm,bottom=4.dp)) {
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
                Spacer(Modifier.height(AppUi.SpaceSm))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(AppUi.SpaceSm)) {
                    Text("目标频宽",style=MaterialTheme.typography.titleMedium)
                    ScrModSelectionGroup(listOf(20,40,80),selectedBandwidth,
                        { onAutoSettingsChanged(autoSettings.copy(bandwidthMhz=it)) },{ "$it MHz" },
                        modifier=Modifier.weight(1f),enabled=!isBusy && isFiveGhz && selected.channel!=null)
                }
                bandSettingsMessage?.takeIf { bandSettingsMessageIsError }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(AppUi.SpaceSm)) {
                    Column(Modifier.weight(1f)) {
                        Text("热点重开后恢复",style=MaterialTheme.typography.titleMedium)
                        Text("恢复已保存的信道与目标频宽一次，允许系统后续降宽",style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=autoSettings.restoreOnHotspotStart,enabled=!isBusy && (autoSettings.restoreOnHotspotStart || (isFiveGhz && selected.channel!=null)),
                        onCheckedChange={onAutoSettingsChanged(autoSettings.copy(restoreOnHotspotStart=it,bandwidthMhz=selectedBandwidth))})
                }
                if(autoSettings.restoreOnHotspotStart && hotspotRestoreMessage.isNotBlank()) Text(hotspotRestoreMessage,style=MaterialTheme.typography.bodySmall)
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

            }
        }

    }

    if (showApplyConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("应用热点设置？") },
            text = { Text(selected.channel?.let { "热点将切换到信道 $it、$selectedBandwidth MHz；连接可能短暂中断。" } ?: "将停止固定信道控制，交由系统管理。") },
            confirmButton = {
                ScrModTextButton(onClick = {
                    showApplyConfirm = false
                    operation = UiOperationState(UiOperationPhase.RUNNING, "正在应用信道")
                    scope.launch {
                        try {
                        val controller = ChannelController(context, RootCommandExecutor {}, {})
                        onAutoSettingsChanged(if(selected.channel!=null) autoSettings.copy(channel=selected.channel!!,bandwidthMhz=selectedBandwidth)
                            else autoSettings.copy(restoreOnHotspotStart=false))
                        val result = controller.apply(selected.channel, status, moduleAsset, hotspot, selected.channel?.let { selectedBandwidth })
                        val message = if (result.success) {
                            result.message
                        } else if (result.rollback?.state == KernelRollbackState.ROLLBACK_FAILED) {
                            "应用失败，未确认恢复到安全状态；请查看诊断日志"
                        } else if (result.kernelVerification == KernelVerificationState.VERIFICATION_UNAVAILABLE ||
                            result.kernelVerification == KernelVerificationState.VERIFICATION_FAILED
                        ) {
                            "安全验证未通过，未切换信道；请查看诊断日志"
                        } else {
                            result.actualStatus?.let { "未确认目标设置，当前信道 ${it.channel ?: "未知"} · 频宽 ${it.bandwidthMhz?.let { width -> "$width MHz" } ?: "未报告"}" } ?: result.message
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
        Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
