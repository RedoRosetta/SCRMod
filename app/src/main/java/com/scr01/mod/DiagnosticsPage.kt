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

@Composable
internal fun SettingsPage(
    autoSettings: AutoChannelSettings,
    onAutoSettingsChanged: (AutoChannelSettings) -> Unit,
    status: DeviceStatus,
    moduleAsset: ModuleAssetStatus,
    logs: List<String>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ScrModPage(modifier) {
        ScrModCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(AppUi.SectionPadding)) {
                SettingSwitch("开机自动应用设置", "应用已保存的设置", autoSettings.applyAfterBoot,
                    onCheckedChange = { onAutoSettingsChanged(autoSettings.copy(applyAfterBoot = it)) })
            }
        }

        DiagnosticLogCard(logs, context)
        ScrModCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                Text("关于 SCRMod", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text("SCR-01 设备增强与诊断", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                AboutInfoRow("版本", displayAppVersion(context))
                AboutInfoRow("设备", status.model)
                AboutInfoRow("固件", status.firmwareName)
            }
        }
    }
}


@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

