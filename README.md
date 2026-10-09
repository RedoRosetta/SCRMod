<p align="center"><img src="scrmod-app-icon.png" width="112" alt="SCRMod 应用图标"></p>

<h1 align="center">SCRMod</h1>

<p align="center"><strong>Advanced Control &amp; Diagnostics for Samsung SCR-01</strong><br>把热点控制、数据上限与设备诊断，放进一个清晰的界面。</p>

<p align="center">
<img src="https://img.shields.io/badge/Device-SCR--01-466775?style=flat-square" alt="目标设备 SCR-01">
<img src="https://img.shields.io/badge/Android-11-466775?style=flat-square" alt="目标系统 Android 11">
<img src="https://img.shields.io/badge/Release-1.0_build11-466775?style=flat-square" alt="当前版本 1.0 build11">
<img src="https://img.shields.io/badge/SMS-Beta-8a6d3b?style=flat-square" alt="短信功能处于 Beta 阶段">
</p>

<p align="center"><a href="https://github.com/RedoRosetta/SCRMod/releases/download/v1.0-build11/SCRMod-1.0-build11-v40-slim-ui-aligned-v9.apk"><strong>下载 APK</strong></a> · <a href="#下载与安装">安装指南</a> · <a href="https://github.com/RedoRosetta/SCRMod/releases/tag/v1.0-build11">版本说明</a> · <a href="https://github.com/RedoRosetta/SCRMod/issues/new/choose">问题反馈</a> · <a href="#源码与许可">源码范围</a></p>

<p align="center">Samsung Galaxy 5G Mobile Wi-Fi SCR-01 / SM-H412J · Android 11 · 横竖屏与深浅色模式</p>

> **安装前请了解：** 控制功能需要设备已有的 Root 环境，请先参考 [SCRoot](https://github.com/hackintoanetwork/SCRoot)。完整 APK 保留短信功能修复；公开源码不包含短信与授权实现。三星短信 APP 需另行准备，不随本项目分发。

## 设备状态，一眼看清

SCRMod 专为 SCR-01 设计，将原本分散的热点设置、数据上限和诊断信息集中到一起。首页展示 Root、热点及完整 APK 的短信就绪状态，让你先了解设备，再决定是否调整。

<p align="center"><img src="assets/screenshots/home-original.png" width="960" alt="首页：Root、热点、短信状态与数据使用上限"></p>

**数据上限分别设置。** 支持月度和 3 天两个周期，选择周期、输入上限并应用，也可以直接恢复默认设置。可保存配置并选择开机自动应用；开机设置本身不会授予 Root。

## 热点控制与网络诊断

网络页展示实际热点启停、频段、信道、频率和带宽，并提供系统频段设置与受支持的 5 GHz 信道应用入口。

<p align="center"><img src="assets/screenshots/network-original.png" width="960" alt="网络页：实际热点状态、待应用信道与开机自动应用设置"></p>

当前信道与待应用信道分别显示，截图中的两个值可以不同。实际应用受频段、热点状态、设备固件及安全检查约束。

**NAT 诊断用于了解当前网络出口。** 它不承诺 Full Cone 或 NAT A，也不能改变运营商 CGNAT 的策略。设置页还提供运行记录查看与复制，方便定位设备侧的问题。

## 短信功能修复 · Beta

完整 APK 提供运营商方案选择、短信接收与发送状态、授权管理和重新配置入口；移动方案还提供 IMS 兼容控制。

<p align="center"><img src="assets/screenshots/sms-original.png" width="960" alt="完整 APK：短信功能修复、运营商方案、授权与收发状态"></p>

该功能需要兼容的三星短信 APP、相应授权以及可用的设备环境。兼容性受 APP 版本、固件、运营商、SIM 和 IMS 状态影响，仍处于 Beta 阶段，不保证所有场景的收发结果。

短信、IMS、注入资产及授权实现不包含在公开源码中。三星官方 APP 不包含在本仓库或 SCRMod APK 中。

*以上三张图片来自维护者提供的真实运行截图，已统一裁去窗口边框与系统栏，保留原始页面像素。图中的就绪状态只反映拍摄时的设备，不代表所有运营商和使用场景都已通过验收。*

## 下载与安装

1. 按 [SCRoot 项目说明](https://github.com/hackintoanetwork/SCRoot)准备目标设备，取得临时 Root，并确认 KernelSU-Next 权限可用。
2. 下载 [SCRMod 1.0 (build11) APK](https://github.com/RedoRosetta/SCRMod/releases/download/v1.0-build11/SCRMod-1.0-build11-v40-slim-ui-aligned-v9.apk)。GitHub 自动生成的 **Source code** 是公开版源码，不能作为安装包使用。
3. 安装并授予 SCRMod Root 权限，先查看首页检查结果，再按需调整热点与数据上限。
4. 使用短信修复前，另行准备兼容的三星短信 APP，并完成应用内要求的授权。

当前安装包约 **25.7 MiB**，`versionCode 40`。[Release 与校验文件](https://github.com/RedoRosetta/SCRMod/releases/tag/v1.0-build11)

<details>
<summary>安装包校验与升级签名</summary>

SHA-256：

```text
4f919a9f3adc36a25b746452b5cbf253197418e2a310ef8a2b02f83816a2e298
```

APK 为非 debuggable 的优化 Release 构建，已启用 R8 与资源压缩。为了保持已有设备的覆盖升级能力，沿用历史 Android debug 证书，尚未切换独立生产签名证书。同签名版本可覆盖安装保留数据；签名冲突时不要直接卸载，以免丢失配置与授权。

</details>

## 兼容性与运行条件

| 项目 | 当前目标环境 |
| --- | --- |
| 设备 | Samsung Galaxy 5G Mobile Wi-Fi SCR-01 / SM-H412J |
| 系统 | Android 11 |
| 固件 | SCR01KDU1AVK2 |
| 内核 | 4.14.186-24165939 |
| Root | SCRoot 提供的临时 Root 与可用的 KernelSU-Next 权限 |
| 其他设备或固件 | 未保证兼容；控制功能受设备与模块检查约束 |

SCRMod 使用已有的 Root 权限，不负责解锁 Bootloader、获取永久 Root 或自动恢复提权。临时 Root 在重启后失效，重新获取权限应按 SCRoot 的说明处理。

## 功能边界与已知限制

- **设备与信道：** 实际支持范围取决于硬件、驱动、固件及安全检查，不是通用 Android 热点控制工具。
- **短信：** 页面显示就绪或安装启动成功，不等于实际短信收发已验证。
- **网络：** 本地诊断不能绕过上游网络限制，不提供 Full Cone 或 NAT A 保证。
- **界面：** 应用以简体中文为主，英文副标题不表示已提供完整英文界面。

完整 APK 已完成构建、135 项测试以及目标 SCR-01 的覆盖安装与启动核对；公开源码版已通过独立构建和 59 项测试。这些结果与真实 UI、短信及网络场景验收分别说明。

## 源码与许可

| 材料 | 公开源码 | 完整 APK |
| --- | --- | --- |
| UI、设备检查、热点与信道控制、数据上限、NAT 诊断 | 包含 | 包含 |
| 短信修复、IMS 兼容、注入资产及后台任务 | 不包含 | 保留 |
| 授权实现与签发工具 | 不包含 | 仅含客户端授权功能 |
| 三星短信 APP、SCRoot 提权程序、私钥 | 不包含 | 不包含 |

公开源码可以构建基础功能版，**不能重建包含短信修复的完整 APK**。[源码范围](docs/SOURCE_SCOPE.md) · [构建说明](docs/BUILD.md) · [第三方材料](THIRD_PARTY_NOTICES.md)

自有应用源码暂未授予开源许可证，版权由作者保留；第三方组件与资产遵循各自许可证。信道模块目前只有带 GPL 标记的预编译二进制，对应 C 源码与构建材料仍待找回；其他第三方材料缺项见第三方清单，不宣称完整可复现构建或许可审查已完成。

## 项目基础与致谢

SCRMod 依托 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 为 SCR-01 提供的临时 Root 与 KernelSU-Next 支持运行。感谢 **hackintoanetwork 及 SCRoot 项目贡献者**对这款设备的适配工作。

SCRoot 与 SCRMod 为独立项目。SCRMod 使用其建立的 Root 环境，不实现或捆绑 SCRoot 的提权流程，也不重新分发其安装包。

## 问题反馈

欢迎通过 [GitHub Issues](https://github.com/RedoRosetta/SCRMod/issues/new/choose)反馈错误、兼容性问题和功能建议。请提供设备型号、固件、应用版本、操作步骤、预期结果与实际结果。

上传日志或截图前，请删除短信内容、手机号、设备码、授权文件、密钥、序列号、IP 地址等不希望公开的信息。[安全说明](SECURITY.md)

---

<p align="center"><strong>SCRMod</strong><br>Advanced Control &amp; Diagnostics for Samsung SCR-01<br>Developed by <a href="https://github.com/RedoRosetta">RedoRosetta</a></p>
