package com.scr01.mod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The product UI only measures the local egress; Full Cone controls remain research-only.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NatDiagnosticsCard(addLog: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("nat-feature", 0) }
    var server by rememberSaveable { mutableStateOf(prefs.getString("server", "").orEmpty()) }
    var operation by remember { mutableStateOf(UiOperationState()) }
    val testing = operation.running
    var testJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<NatTestResult?>(null) }
    var showServerDialog by rememberSaveable { mutableStateOf(false) }
    var serverDraft by rememberSaveable { mutableStateOf("") }
    var serverError by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { testJob?.cancel() } }
    ScrModCard(Modifier.fillMaxWidth(), shape = AppUi.CardShape) {
        Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
                        Text("NAT 诊断", style = MaterialTheme.typography.titleLarge)
                        Text("检测当前设备的网络出口", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val actions: @Composable () -> Unit = {
            FlowRow(modifier = Modifier.wrapContentWidth(), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
            ScrModButton(onClick = {
                if (testing) {
                    generation++; testJob?.cancel(); result = null; progress = ""
                    operation = UiOperationState(UiOperationPhase.CANCELLED, "检测已取消")
                    return@ScrModButton
                }
                val run = ++generation
                operation = UiOperationState(UiOperationPhase.RUNNING)
                result = null; progress = "正在准备检测"
                testJob = scope.launch {
                    try {
                        val measured = NatDiscovery.test(server) { message -> scope.launch { if (run == generation) progress = message } }
                        if (run == generation) {
                            result = measured
                            operation = UiOperationState(if (measured.endpoint.isEmpty()) UiOperationPhase.FAILED else if (measured.mapping == NatMapping.UNKNOWN && !measured.direct) UiOperationPhase.UNKNOWN else UiOperationPhase.SUCCEEDED)
                            addLog("NAT 检测：${measured.title}；${measured.endpoint}；${measured.server}")
                        }
                    } catch (e: CancellationException) {
                        if (run == generation) operation = UiOperationState(UiOperationPhase.CANCELLED, "检测已取消")
                        throw e
                    } catch (e: Exception) {
                        if (run == generation) {
                            result = NatTestResult("检测失败", e.message.orEmpty())
                            operation = UiOperationState(UiOperationPhase.FAILED)
                        }
                    }
                }
            }, modifier = Modifier.widthIn(min = 100.dp)) { Text(if (testing) "取消检测" else "一键测试") }
            ScrModSecondaryButton(onClick = {
                serverDraft = server; serverError = false; showServerDialog = true
            }, enabled = !testing) { Text("自定义地址") }
            }
                }
                if (useWideLayout(maxWidth)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                    Box(Modifier.weight(1f)) { heading() }; actions()
                } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                    heading(); Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions() }
                }
            }
            if (server.isNotBlank()) Text("检测地址：$server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (testing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress) }
            if (operation.phase == UiOperationPhase.CANCELLED) Text(operation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result?.let {
                HorizontalDivider()
                Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(it.letterType()?.let { grade -> "类型 $grade" } ?: "类型未判定",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (it.endpoint.isNotEmpty()) Text("公网映射：${it.endpoint}", style = MaterialTheme.typography.bodyMedium)
                if (it.title.startsWith("疑似") || it.endpoint.isEmpty() || it.mapping == NatMapping.UNKNOWN && !it.direct) {
                    Text(it.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (it.server.isNotEmpty()) Text("服务器：${it.server}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showServerDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showServerDialog = false },
            title = { Text("自定义检测地址") },
            text = {
                ScrModTextField(
                    value = serverDraft,
                    onValueChange = { serverDraft = it; serverError = false },
                    label = { Text("域名或 IPv4:端口") },
                    placeholder = { Text("例如 stun.example.com:3478") },
                    supportingText = { Text(if (serverError) "请输入正确的地址和端口" else "留空使用自动选择") },
                    isError = serverError, singleLine = true, modifier = Modifier.fillMaxWidth(),                 )
            },
            confirmButton = {
                ScrModTextButton(onClick = {
                    val value = serverDraft.trim()
                    val parts = value.split(':')
                    val valid = value.isBlank() || parts.size == 2 &&
                        parts[0].matches(Regex("[a-zA-Z0-9.-]{1,253}")) &&
                        parts[1].toIntOrNull()?.let { it in 1..65535 } == true
                    if (valid) {
                        server = value; prefs.edit().putString("server", value).apply(); showServerDialog = false
                    } else serverError = true
                }) { Text("保存") }
            },
            dismissButton = { ScrModTextButton(onClick = { showServerDialog = false }) { Text("取消") } },
        )
    }
}
