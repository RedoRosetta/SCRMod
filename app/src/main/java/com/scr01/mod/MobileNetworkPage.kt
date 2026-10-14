package com.scr01.mod

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun MobileNetworkPage(modifier: Modifier = Modifier, addLog: (String)->Unit = {}) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val owner=LocalLifecycleOwner.current
    val monitor=remember(context) { CellularMonitor(AndroidCellInfoSource(context),AndroidCellClock()) }
    val state by monitor.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var settingsError by remember { mutableStateOf<String?>(null) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        monitor.stop(); if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) monitor.start()
    }
    DisposableEffect(monitor,owner) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_START -> monitor.start()
            Lifecycle.Event.ON_STOP -> monitor.stop()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) monitor.start()
        onDispose { owner.lifecycle.removeObserver(observer); monitor.close() }
    }
    LaunchedEffect(state.active) { while(state.active) { now=SystemClock.elapsedRealtime();delay(5000) } }
    val all=state.batch.cells
    val missing=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED
    val order=remember { mutableStateListOf<String>() }
    val expanded=remember { mutableStateMapOf<String,Boolean>() }
    val sorted=all.sortedWith(compareByDescending<CellularCell> { it.serving }.thenBy { it.rat.name }.thenByDescending { it.rsrp ?: Int.MIN_VALUE })
    LaunchedEffect(sorted.map(CellVisuals::key)) {
        val keys=sorted.map(CellVisuals::key)
        order.removeAll { it !in keys }; keys.forEach { if(it !in order) order.add(it) }
    }
    val display=sorted.sortedWith(compareByDescending<CellularCell> { it.serving }.thenBy { order.indexOf(CellVisuals.key(it)).let { rank -> if(rank<0) Int.MAX_VALUE else rank } })
    val entries=display.mapIndexed { index,cell -> (CellVisuals.key(cell)+":"+display.take(index).count { CellVisuals.key(it)==CellVisuals.key(cell) }) to cell }
    LazyColumn(modifier.fillMaxSize(),contentPadding=AppUi.PagePadding,verticalArrangement=Arrangement.spacedBy(AppUi.SpaceSm)) {
        item {
            ScrModCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppUi.SectionPadding)) {
                ActionCardHeader("蜂窝网络频段",CellConnectionLabel.current(all,state.batch.dataRat),actions={
                    ScrModSecondaryButton(onClick={settingsError=SamsungMobileNetworkSettings.open(context).exceptionOrNull()?.let { "无法打开系统设置" }}) { Text("设置") }
                })
            } }
        }
        item { NatDiagnosticsCard(addLog) }
        item {
            ScrModCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppUi.SectionPadding),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("小区信息",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    ScrModSecondaryButton(enabled=state.active&&!state.refreshing,onClick={
                        if(missing) permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE))
                        else { order.clear(); monitor.refresh() }
                    }) { Text(if(missing) "授予权限" else if(state.refreshing) "刷新中…" else "刷新") }
                }
                Text("当前小区与邻区 · 共 ${all.size} 个",style=MaterialTheme.typography.bodySmall)
                Text("−140～−60 dBm 参考刻度；不代表网速或切换优先级",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                state.problem?.let { Text(it.message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                if(all.none { it.serving && it.role!=CellRole.CONFLICT }) Text("当前小区等待注册信息交叉确认",style=MaterialTheme.typography.bodySmall)
                if(missing || state.problem==CellProblem.LOCATION_OFF) ScrModTextButton(onClick={
                    context.startActivity(if(state.problem==CellProblem.LOCATION_OFF) android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))
                }) { Text("权限与位置设置") }
            } }
        }
        items(entries,key={it.first}) { (key,cell) ->
            CellRowCard(cell,now,expanded[key]==true,{expanded[key]=expanded[key]!=true})
        }
        item { settingsError?.let { Text(it,color=MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun CellRowCard(cell:CellularCell,now:Long,expanded:Boolean,onExpand:()->Unit) {
    val current=cell.serving && cell.role!=CellRole.CONFLICT
    ScrModCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppUi.SectionPadding),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick=onExpand),verticalAlignment=Alignment.CenterVertically) {
                val label=listOfNotNull(if(current) "当前使用" else if(cell.role==CellRole.CONFLICT) "状态待核对" else null,
                    if(cell.rat==CellRat.NR) "5G" else "4G",cell.bandLabel.takeUnless { it=="未知" },cell.pci?.let { "PCI $it" })
                Text(label.joinToString(" · "),Modifier.weight(1f),fontWeight=FontWeight.Medium)
                Text(cell.rsrp?.let { "$it dBm" } ?: "未测得")
                Text(if(expanded) "  −" else "  +")
            }
            cell.rsrp?.let { dbm ->
                val color=when {dbm>=-95->Color(0xFF38A878);dbm>=-110->Color(0xFFD2AA3A);dbm>=-120->Color(0xFFD77B4D);else->Color(0xFFBF5C63)}
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawRoundRect(Color(0xFF36515C),cornerRadius=androidx.compose.ui.geometry.CornerRadius(6f,6f))
                    drawRoundRect(color,size=androidx.compose.ui.geometry.Size(size.width*CellVisuals.fraction(dbm),size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(6f,6f))
                }
            }
            val measurement=if(cell.rat==CellRat.NR) "同步信号强度（SS-RSRP）" else "参考信号强度（RSRP）"
            Text(measurement+" · "+(cell.ageMs(now)?.let { "${it/1000} 秒前" } ?: "测量时间未报告"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(expanded) {
                HorizontalDivider()
                cell.arfcn?.let { CellParameter(if(cell.rat==CellRat.NR) "频点（NR-ARFCN）" else "频点（EARFCN）",it.toString()) }
                cell.rsrp?.let { CellParameter(if(cell.rat==CellRat.NR) "信号强度（SS-RSRP）" else "信号强度（RSRP）","$it dBm") }
                cell.rsrq?.let { CellParameter(if(cell.rat==CellRat.NR) "信号质量（SS-RSRQ）" else "信号质量（RSRQ）","$it dB") }
                cell.sinr?.let { CellParameter("信噪比（${cell.sinrLabel}）","$it dB") }
                cell.identity?.let { CellParameter(if(cell.rat==CellRat.NR) "小区标识（NCI）" else "小区标识（CI）",it.toString()) }
                cell.tac?.let { CellParameter("跟踪区域码（TAC）",it.toString()) }
                cell.plmn?.let { CellParameter("网络代码（PLMN）",it) }
                if(cell.registrationEvidence) CellParameter("当前小区依据","分组域注册信息精确匹配")
                else CellParameter("连接依据",if(current) "系统注册与连接标记" else "系统小区测量")
            }
        }
    }
}

@Composable
private fun CellParameter(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,Modifier.weight(.7f),style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}
