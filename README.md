<img src="scrmod-app-icon.png" width="96" alt="SCRMod 应用图标">

# SCRMod

面向 Samsung Galaxy 5G Mobile Wi-Fi SCR-01 / SM-H412J 的设备控制与诊断应用。

Device controls and diagnostics for the Samsung SCR-01 / SM-H412J mobile router.

SCRMod 将热点状态、5 GHz 信道设置、数据使用上限和诊断记录集中在一个界面中，支持横竖屏与深浅色模式。完整安装包另提供短信功能修复；短信实现不在公开源码中。

The app brings hotspot information, 5 GHz channel configuration, data-limit settings and diagnostic logs into one interface. It adapts to portrait and landscape and supports light and dark themes. The full APK also offers SMS repair; that implementation is not part of the public source.

## 页面预览 / Interface Preview

### 首页 / Home

查看 Root、热点和短信就绪状态；分别设置月度与 3 天数据上限。

![首页：状态总览与数据使用上限](assets/screenshots/home-original.png)

### 网络 / Network

查看实际热点频段、信道和带宽，再选择待应用的信道。图中的当前信道与待应用值可以不同。

![网络：热点状态与信道设置](assets/screenshots/network-original.png)

### 短信功能修复 / SMS Repair

完整 APK 提供运营商方案、收发状态、授权管理和重新配置。该页面不包含在公开源码构建中。

![完整 APK 的短信功能修复界面](assets/screenshots/sms-original.png)

截图由维护者提供，展示拍摄时的界面和状态；不据此保证所有 SIM、运营商或网络条件下的功能结果。

## 项目基础与致谢 / Foundation and Acknowledgments

本项目依托 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 为 SCR-01 提供的临时 Root 与 KernelSU-Next 支持运行。感谢 hackintoanetwork 及 SCRoot 项目贡献者对 SCR-01 的适配工作。SCRMod 使用设备已有的 Root 环境，不实现或捆绑 SCRoot 的提权流程；SCRoot 与 SCRMod 为独立项目。

SCRMod relies on the temporary root and KernelSU-Next environment provided by SCRoot. Thanks to hackintoanetwork and the SCRoot contributors for supporting this device. Root setup belongs to SCRoot; SCRMod uses the resulting permissions to control and inspect the router.

## 下载 / Download

完整安装包通过 [Releases](https://github.com/RedoRosetta/SCRMod/releases) 提供。源码下载用于构建不含短信实现的公开版，不能重建包含短信修复的完整 APK。

Download the full APK from [Releases](https://github.com/RedoRosetta/SCRMod/releases). The public source builds a version without SMS repair and cannot reproduce the full APK.

当前版本：`1.0 (build11)`，`versionCode 40`。安装包约 25.7 MiB。

APK: `SCRMod-1.0-build11-v40-slim-ui-aligned-v9.apk`.

SHA-256: `4f919a9f3adc36a25b746452b5cbf253197418e2a310ef8a2b02f83816a2e298`



## 主要功能 / Main Features

- 查看 Root、设备和固件检查结果。 Inspect root availability and device compatibility.
- 查看热点启停、频段、信道、频率和带宽。 Read hotspot state, band, channel, frequency and bandwidth.
- 打开系统频段设置，并在检查通过时应用受支持的 5 GHz 信道。 Open system band settings and apply supported channels behind compatibility checks.
- 分别设置月度与 3 天的数据使用上限，支持恢复默认。 Configure separate monthly and three-day usage limits and restore defaults.
- 保存设置并选择开机自动应用。 Save settings and opt into applying them after boot.
- 进行本机 NAT 出口诊断、查看和复制运行记录。 Run local NAT diagnostics and review logs.
- 完整安装包提供短信功能修复及离线授权管理；公开源码版不包含这些模块。 The full APK includes SMS repair and offline authorization; the public source excludes these modules.

## 兼容性与前置条件 / Compatibility and Requirements

| 项目 / Item | 目标 / Target |
| --- | --- |
| 设备 / Device | Samsung SCR-01 / SM-H412J |
| 系统 / Android | Android 11 |
| 固件 / Firmware | SCR01KDU1AVK2 |
| 内核 / Kernel | 4.14.186-24165939 |
| Root 环境 / Root environment | SCRoot 临时 Root、可用的 KernelSU-Next 权限 |

控制功能受设备、固件、内核和模块完整性检查约束，不是面向所有 Android 热点设备的通用工具。

Controls depend on the supported device profile and module integrity. This is not a generic Android hotspot tool.

SCRoot 的临时 Root 在重启后失效；是否重新获得权限由 SCRoot 的配置和运行结果决定。SCRMod 的开机自动应用设置不等于自动获得 Root。

Temporary root is lost on reboot. Root recovery is handled by SCRoot; SCRMod's boot setting does not grant root access.

## 安装 / Installation

1. 按 SCRoot 项目说明准备目标设备并取得临时 Root。 Set up temporary root using the SCRoot instructions.
2. 从 Releases 获取完整 APK 并安装。同签名版本可覆盖升级保留数据。 Install the full APK; matching signatures allow an in-place update.
3. 为 SCRMod 授予 Root 权限，确认首页检查结果。 Grant root to SCRMod and check the reported status.
4. 按需调整热点、数据上限和开机设置。 Configure only the controls you need.
5. 短信修复另外依赖用户自行准备的兼容三星短信 APP，以及应用内要求的授权。该三星 APP 不随本仓库或 SCRMod APK 提供。 SMS repair additionally requires a compatible Samsung messaging app supplied separately by the user and the authorization required by SCRMod. No Samsung APK is distributed here.

当前完整 APK 为非 debuggable、经过压缩的 Release 构建，沿用历史 Android debug 证书签名以保持现有设备覆盖升级。它不是使用独立生产证书签署的版本。

The APK is a non-debuggable, optimized Release build signed with the historical Android debug certificate for upgrade compatibility. It does not yet use a dedicated production signing certificate.

## 已知限制 / Known Limitations

- 实际信道应用取决于当前频段、热点状态和安全检查。 Channel changes depend on the active band and safety checks.
- 临时 Root 失效、重启或系统变化可能影响功能。 Root loss, reboot or system changes may affect operation.
- 短信行为受三星短信 APP 版本、运营商、SIM 和设备状态影响；安装启动成功不代表收发验收完成。 SMS results depend on the messaging app, carrier, SIM and device state; startup does not validate message delivery.
- NAT 功能用于诊断当前出口，不提供 Full Cone 或 NAT A 保证。 NAT diagnostics do not promise Full Cone or NAT A.
- 当前界面以简体中文为主，英文 README 不表示应用已完整英文化。 The app primarily uses Simplified Chinese; this README does not imply full English UI support.

## 源码公开范围 / Public Source Scope

本仓库公开 UI、热点与信道控制、数据上限、Root 命令执行、状态读取和诊断相关源码。短信收发修复、IMS 兼容实现、注入脚本和运行程序、短信后台任务、授权实现与签发工具均不公开。

UI, hotspot/channel controls, data-limit handling, root command execution and diagnostics are included. SMS repair, IMS compatibility, injection assets, SMS workers, authorization and issuer tools are excluded.

完整 APK 与公开源码版有意保持不同的功能范围。详见 [源码范围](docs/SOURCE_SCOPE.md) 和 [构建说明](docs/BUILD.md)。

## 反馈 / Feedback

提交 Issue 时请附设备型号、固件、应用版本、相关功能、操作步骤及实际结果。日志上传前请删去手机号、短信内容、设备码、授权文件、序列号、IP 地址和其他不希望公开的信息。

When reporting a problem, include the model, firmware, app version, steps and observed result. Redact personal identifiers, message contents and credentials before sharing logs.

## 第三方材料 / Third-Party Materials

见 [第三方清单](THIRD_PARTY_NOTICES.md)。本项目不会重新分发三星官方 APP 或 SCRoot 安装包。公开的自有源码暂未授予开源许可证，版权由作者保留；第三方组件遵循各自许可证。
