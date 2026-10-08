package com.scr01.mod

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Pure presentation fixtures: never create managers or perform device operations. */
@Preview(name = "Portrait light", widthDp = 360, heightDp = 740, showBackground = true)
@Preview(name = "Portrait dark", widthDp = 360, heightDp = 740, uiMode = 0x20, showBackground = true)
@Preview(name = "Landscape light", widthDp = 692, heightDp = 360, showBackground = true)
@Preview(name = "Landscape dark", widthDp = 692, heightDp = 360, uiMode = 0x20, showBackground = true)
@Preview(name = "Portrait large text", widthDp = 360, heightDp = 740, fontScale = 1.5f, showBackground = true)
@Preview(name = "Landscape large text", widthDp = 692, heightDp = 360, fontScale = 1.5f, showBackground = true)
@Composable
internal fun UiSystemPreview() {
    Scr01ModTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScrModPage {
                Text("SCRMod", style = MaterialTheme.typography.headlineSmall)
                ScrModCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        Text("设备状态", style = MaterialTheme.typography.titleLarge)
                        StatusLight("已就绪", UiStatus.SUCCESS)
                        StatusLight("正在检查", UiStatus.LOADING)
                        StatusLight("等待系统服务", UiStatus.WAITING)
                        StatusLight("设备身份暂不可用", UiStatus.UNKNOWN)
                        StatusLight("需要处理", UiStatus.ERROR)
                        StatusLight("未开启", UiStatus.DISABLED)
                    }
                }
                ScrModAdaptivePair(
                    first = { ScrModButton({}, it) { Text("应用") } },
                    second = { ScrModSecondaryButton({}, it) { Text("导入授权文件") } },
                )
                ScrModTextField("2000", {}, Modifier.fillMaxWidth(), suffix = { Text("GB") })
                ScrModTextField("", {}, Modifier.fillMaxWidth(), isError = true,
                    supportingText = { Text("请输入 1～2000 GB 的整数") })
                SettingSwitch("开机自动应用设置", "应用已保存的设置；长说明应换行且保留完整开关", false, onCheckedChange = {})
                ScrModButton({}, enabled = false) { Text("正在处理…") }
            }
        }
    }
}
