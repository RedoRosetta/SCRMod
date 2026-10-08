package com.scr01.mod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.graphics.drawable.toBitmap

@Composable
internal fun HomePage(
    status: DeviceStatus,
    onOpenNetwork: () -> Unit,
    hotspot: HotspotStatus,
    dataUsagePeriod: DataUsagePeriod,
    onDataUsagePeriodChanged: (DataUsagePeriod) -> Unit,
    rootRequestBusy: Boolean,
    onRequestRoot: () -> Unit,
    dataUsageConfig: DataUsageMaxOverrideConfig,
    dataUsageInput: String,
    dataUsageBusy: Boolean,
    dataUsageMessage: String?,
    dataUsageMessageIsError: Boolean,
    modifier: Modifier = Modifier,
    onDataUsageInputChanged: (String) -> Unit,
    onApplyDataUsage: () -> Unit,
    onDisableDataUsage: () -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val inputWidth = (128 * configuration.fontScale.coerceAtLeast(1f)).dp
    val version = remember(context) {
        displayAppVersion(context)
    }
    val logo = remember(context) {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap(96, 96).asImageBitmap()
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        ScrModPage(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                Image(logo, contentDescription = null, modifier = Modifier.size(36.dp))
                Column {
                    Text("SCRMod", style = MaterialTheme.typography.headlineSmall, color = palette.onSurface)
                    Text(version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = AppUi.CardShape,
                color = MaterialTheme.colorScheme.surface) {
                BoxWithConstraints(Modifier.padding(AppUi.SectionPadding), contentAlignment = Alignment.Center) {
                    val items: @Composable (Boolean) -> Unit = { wide ->
                        val itemModifier = if (wide) Modifier.width((maxWidth - AppUi.SpaceLg) / 2) else Modifier.fillMaxWidth()
                        HomeStatusItem(Icons.Default.Security, "Root", rootStatusLabel(status.root), status.root == CheckState.Passed,
                            itemModifier, if (status.root == CheckState.Passed) null else onRequestRoot,
                            if (status.root == CheckState.Passed) null else if (rootRequestBusy) "处理中…" else "申请 Root 权限", !rootRequestBusy, compact = !wide)
                        HomeStatusItem(Icons.Default.Wifi, "热点", hotspot.state.label(), hotspot.state == HotspotState.On,
                            itemModifier, onOpenNetwork, compact = !wide)
                    }
                    if (useWideLayout(maxWidth)) Row(horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceLg),
                        verticalAlignment = Alignment.CenterVertically) { items(true) }
                    else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) { items(false) }
                }
            }

            val summary: @Composable (Modifier) -> Unit = { sectionModifier ->
                Surface(sectionModifier, shape = AppUi.CardShape, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val wide = useWideLayout(maxWidth)
                            val editModifier = Modifier.fillMaxWidth()
                            val periods: @Composable () -> Unit = {
                                ScrModSelectionGroup(DataUsagePeriod.entries, dataUsagePeriod, onDataUsagePeriodChanged,
                                    { if (it == DataUsagePeriod.Month) "1月" else "3天" },
                                    modifier = Modifier.width((144 * configuration.fontScale.coerceAtLeast(1f)).dp), enabled = !dataUsageBusy)
                            }
                            val field: @Composable (Modifier) -> Unit = { fieldModifier ->
                                ScrModTextField(value = dataUsageInput, onValueChange = onDataUsageInputChanged,
                                    modifier = fieldModifier, enabled = !dataUsageBusy, singleLine = true,
                                    isError = dataUsageInput.isNotBlank() && !dataUsageBusy && DataUsageMaxValidator.parseTarget(dataUsageInput).isFailure,
                                    placeholder = { Text("上限") }, suffix = { Text("GB") },
                                    textStyle = typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            val apply: @Composable () -> Unit = {
                                ScrModButton(onClick = onApplyDataUsage,
                                    enabled = !dataUsageBusy && !dataUsageConfig.recoveryPending &&
                                        DataUsageMaxValidator.parseTarget(dataUsageInput).isSuccess) {
                                    Text(if (dataUsageBusy) "处理中…" else "应用")
                                }
                            }
                            val reset: @Composable () -> Unit = {
                                ScrModSecondaryButton(onClick = onRestoreDefaults, enabled = !dataUsageBusy) {
                                    Text("恢复默认")
                                }
                            }
                            if (wide) Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                                Text("数据使用上限", style = typography.titleLarge)
                                Row(editModifier, verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                                    periods(); field(Modifier.weight(1f).widthIn(min = inputWidth)); apply(); reset()
                                }
                            } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                                Text("数据使用上限", style = typography.titleLarge)
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    if (maxWidth >= (240 * configuration.fontScale.coerceAtLeast(1f)).dp) Row(
                                        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween) { periods(); reset() }
                                    else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) { periods(); reset() }
                                }
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    if (maxWidth >= (240 * configuration.fontScale.coerceAtLeast(1f)).dp) Row(
                                        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                                        field(Modifier.weight(1f)); apply()
                                    } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                                        field(Modifier.fillMaxWidth()); apply()
                                    }
                                }
                            }
                        }
                        if (dataUsageInput.isNotBlank() && !dataUsageBusy &&
                            DataUsageMaxValidator.parseTarget(dataUsageInput).isFailure) {
                            Text("请输入 1～2000 GB 的整数", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        dataUsageMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = if (dataUsageMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            summary(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeStatusItem(icon: ImageVector, title: String, status: String, ready: Boolean,
    modifier: Modifier, onClick: (() -> Unit)? = null, action: String? = null, enabled: Boolean = true, compact: Boolean = false) {
    BoxWithConstraints(modifier) {
        val heading: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val state: @Composable () -> Unit = {
            if (onClick == null) StatusLight(status, ready)
            else if (action != null) {
                ScrModSecondaryButton(onClick = onClick, enabled = enabled,                     contentPadding = AppUi.ButtonPadding) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                Box(Modifier.size(7.dp).background(statusColor(if (ready) UiStatus.SUCCESS else UiStatus.UNKNOWN), CircleShape))
                ScrModTextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = AppUi.SpaceXs, vertical = AppUi.SpaceSm)) {
                    Text(status, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, Modifier.size(16.dp))
                }
            }
        }
        if (compact) Row(Modifier.fillMaxWidth().heightIn(min = AppUi.TouchHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
            Box(Modifier.weight(1f)) { heading() }
            if (action != null) state()
            else {
                val stateWidth = (128 * androidx.compose.ui.platform.LocalConfiguration.current.fontScale.coerceAtLeast(1f)).dp
                val stateModifier = Modifier.width(stateWidth).heightIn(min = AppUi.TouchHeight)
                Row(if (onClick != null) stateModifier.clickable(enabled = enabled, onClick = onClick) else stateModifier,
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                    Box(Modifier.size(7.dp).background(statusColor(if (ready) UiStatus.SUCCESS else UiStatus.UNKNOWN), CircleShape))
                    Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (onClick != null) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    else Spacer(Modifier.size(16.dp))
                }
            }
        } else if (maxWidth >= (190 * androidx.compose.ui.platform.LocalConfiguration.current.fontScale.coerceAtLeast(1f)).dp) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm, Alignment.CenterHorizontally)) {
            heading(); state()
        } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
            heading(); state()
        }
    }

}

