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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val resumeTick = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Scr01ModTheme { Scr01ModApp(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value += 1
    }
}

private enum class Page(val label: String) {
    Home("主页"),
    Network("网络"),
    Settings("设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scr01ModApp(resumeTick: StateFlow<Int>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resumeTickValue by resumeTick.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Page.Home) }
    val logs = remember { MutableStateFlow(listOf<String>()) }
    val logItems by logs.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf(DeviceStatus()) }
    var hotspot by remember { mutableStateOf(HotspotStatus()) }
    var moduleAsset by remember { mutableStateOf(ModuleAssetStatus()) }
    var autoSettings by remember { mutableStateOf(AutoChannelPreferences.read(context)) }
    var dataUsagePeriod by remember { mutableStateOf(DataUsagePeriod.Month) }
    var rootRequestBusy by remember { mutableStateOf(false) }
    var rootRequestMessage by remember { mutableStateOf<String?>(null) }
    var dataUsageConfig by remember { mutableStateOf(DataUsageMaxOverridePreferences.read(context, dataUsagePeriod)) }
    var dataUsageInput by remember { mutableStateOf("") }
    var dataUsageActual by remember { mutableStateOf<String?>(null) }
    var dataUsageOperation by remember { mutableStateOf(UiOperationState()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun addLog(message: String) {
        if (!isUserFacingLog(message)) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.value = (logs.value + "$time  $message").takeLast(80)
    }

    LaunchedEffect(refreshKey, resumeTickValue, page) {
        addLog(if (refreshKey == 0 && resumeTickValue <= 1) "SCRMod 已启动" else "状态已刷新")
        val executor = RootCommandExecutor(::addLog)
        status = DeviceStatusReader.read(executor, ::addLog)
        hotspot = HotspotStatusReader.read(status.root == CheckState.Passed, executor, ::addLog)
        moduleAsset = ModuleAssetVerifier.verify(context, status, ::addLog)
    }

    LaunchedEffect(refreshKey, resumeTickValue, dataUsagePeriod) {
        val selectedPeriod = dataUsagePeriod
        val executor = RootCommandExecutor(::addLog)
        val controller = DataUsageMaxOverrideController(context, executor, selectedPeriod)
        val config = DataUsageMaxOverridePreferences.read(context, selectedPeriod)
        val result = if (config.enabled || config.recoveryPending) controller.reconcile() else null
        val current = result?.actualValue?.let { DataUsageMaxReadResult(true, it, "") }
            ?: controller.readCurrent()
        if (selectedPeriod != dataUsagePeriod) return@LaunchedEffect
        dataUsageConfig = result?.config ?: config
        dataUsageActual = current.value
        if (dataUsageInput.isBlank()) {
            dataUsageInput = dataUsageConfig.overrideValue ?: current.value?.let(::formatDataUsageGb).orEmpty()
        }
        if (result != null && result.message.isNotBlank() && !dataUsageOperation.running) {
            dataUsageOperation = UiOperationState(if (result.success) UiOperationPhase.SUCCEEDED else if (result.config.recoveryPending) UiOperationPhase.UNKNOWN else UiOperationPhase.FAILED, result.message)
        }
    }

    fun runDataUsageOperation(restoring: Boolean, rawValue: String = "") {
        if (dataUsageOperation.running) return
        dataUsageOperation = UiOperationState(UiOperationPhase.RUNNING, if (restoring) "正在恢复原值" else "正在设置上限")
        scope.launch {
            try {
                val controller = DataUsageMaxOverrideController(context, RootCommandExecutor(::addLog), dataUsagePeriod)
                val result = if (restoring) controller.disableAndRestore() else controller.enableOrUpdate(rawValue)
                dataUsageConfig = result.config
                dataUsageActual = result.actualValue
                dataUsageOperation = UiOperationState(
                    if (result.success) UiOperationPhase.SUCCEEDED else if (result.config.recoveryPending) UiOperationPhase.UNKNOWN else UiOperationPhase.FAILED,
                    result.message,
                )
                if (result.success) dataUsageInput = formatDataUsageGb(result.actualValue ?: rawValue)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                dataUsageConfig = DataUsageMaxOverridePreferences.read(context, dataUsagePeriod)
                dataUsageActual = null
                dataUsageOperation = UiOperationState(UiOperationPhase.UNKNOWN, "操作未确认，请刷新或恢复原值")
                addLog("数据上限操作异常：${error.javaClass.simpleName}")
            } finally {
                if (dataUsageOperation.running) dataUsageOperation = UiOperationState(UiOperationPhase.UNKNOWN, "操作中断，请刷新确认")
            }
        }
    }

    fun requestRoot() {
        if (rootRequestBusy) return
        if (status.root != CheckState.Passed && context.packageManager.getLaunchIntentForPackage("com.rifsxd.ksunext") != null) {
            runCatching { context.startActivity(context.packageManager.getLaunchIntentForPackage("com.rifsxd.ksunext")) }
                .onFailure { rootRequestMessage = "无法打开 Root 管理器，请手动为 SCRMod 授权。" }
            return
        }
        rootRequestBusy = true
        scope.launch {
            try {
                val request = RootCommandExecutor(::addLog, RootExecutableDiscovery(
                    probeTimeoutMs = 30_000L))
                status = DeviceStatusReader.read(request, ::addLog)
                rootRequestMessage = if (status.root == CheckState.Passed) "Root 权限已获取" else "未获得 Root 权限，请在 Root 管理器中为 SCRMod 授权并保存。"
                refreshKey++
            }
            catch (error: Exception) { rootRequestMessage = "权限检测未完成，请重试。" }
            finally { rootRequestBusy = false }
        }
    }

    rootRequestMessage?.let { message -> AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = { rootRequestMessage = null },
        title = { Text("Root 权限") }, text = { Text(message) },
        confirmButton = { ScrModTextButton(onClick = { rootRequestMessage = null }) { Text("知道了") } },
    ) }

    fun applyDataUsage(rawValue: String) = runDataUsageOperation(false, rawValue)
    fun disableDataUsage() = runDataUsageOperation(true)
    fun restoreDataDefaults() {
        if (dataUsageOperation.running) return
        dataUsageOperation = UiOperationState(UiOperationPhase.RUNNING, "正在恢复默认")
        scope.launch {
            try {
                val results = DataUsagePeriod.entries.map { period ->
                    period to DataUsageMaxOverrideController(context, RootCommandExecutor(::addLog), period).restoreFactoryDefault()
                }
                val current = results.first { it.first == dataUsagePeriod }.second
                dataUsageConfig = current.config; dataUsageActual = current.actualValue
                if (current.success) dataUsageInput = "15"
                val ok = results.all { it.second.success }
                dataUsageOperation = UiOperationState(if (ok) UiOperationPhase.SUCCEEDED else UiOperationPhase.UNKNOWN,
                    if (ok) "已恢复默认：1个月 15 GB，3天 15 GB" else results.joinToString("；") { (period, result) -> "${period.label}：${result.message}" })
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { dataUsageOperation = UiOperationState(UiOperationPhase.UNKNOWN, "恢复未确认，请重新查看两个周期的上限") }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val drawerWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.38f).coerceIn(220f, 280f).dp
            ModalDrawerSheet(modifier = Modifier.width(drawerWidth), drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)) {
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "SCRMod",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        "SCR01 / SM-H412J",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Page.entries.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = page == item,
                            onClick = {
                                page = item
                                scope.launch { drawerState.close() }
                            },
                            icon = {
                                val icon = when (item) {
                                    Page.Home -> Icons.Default.Home
                                    Page.Network -> Icons.Default.Wifi
                                    Page.Settings -> Icons.Default.Settings
                                }
                                Icon(icon, contentDescription = null)
                            },
                            shape = AppUi.ControlShape,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.primary, selectedIconColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = AppUi.SpaceMd, vertical = AppUi.SpaceXs),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background),
                    expandedHeight = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp > androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp) 48.dp else 56.dp,
                    title = { Text(page.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                        }
                    },
                )
            },
        ) { padding ->
            when (page) {
                Page.Home -> HomePage(
                    status = status,
                    onOpenNetwork = { page = Page.Network },
                    hotspot = hotspot,
                    dataUsagePeriod = dataUsagePeriod,
                    onDataUsagePeriodChanged = { if (!dataUsageOperation.running) {
                        dataUsagePeriod = it; dataUsageInput = ""; dataUsageActual = null
                        dataUsageConfig = DataUsageMaxOverridePreferences.read(context, it)
                        dataUsageOperation = UiOperationState()
                    } },
                    rootRequestBusy = rootRequestBusy,
                    onRequestRoot = ::requestRoot,
                    dataUsageConfig = dataUsageConfig,
                    dataUsageInput = dataUsageInput,
                    dataUsageBusy = dataUsageOperation.running,
                    dataUsageMessage = dataUsageOperation.message.takeIf { it.isNotBlank() },
                    dataUsageMessageIsError = dataUsageOperation.hasError,
                    modifier = Modifier.padding(padding),
                    onDataUsageInputChanged = { dataUsageInput = it },
                    onApplyDataUsage = { applyDataUsage(dataUsageInput) },
                    onDisableDataUsage = ::disableDataUsage,
                    onRestoreDefaults = ::restoreDataDefaults,
                )

                Page.Network -> HotspotPage(
                    status = status,
                    hotspot = hotspot,
                    moduleAsset = moduleAsset,
                    autoSettings = autoSettings,
                    modifier = Modifier.padding(padding),
                    addLog = ::addLog,
                    onRefresh = { refreshKey++ },
                    onAutoSettingsChanged = { settings ->
                        autoSettings = settings
                        AutoChannelPreferences.save(context, settings)
                    },
                )

                Page.Settings -> SettingsPage(
                    autoSettings = autoSettings,
                    onAutoSettingsChanged = { settings ->
                        autoSettings = settings
                        AutoChannelPreferences.save(context, settings)
                    },
                    status = status, moduleAsset = moduleAsset, logs = logItems,
                    onRefresh = { refreshKey++ },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

