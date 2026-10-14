<p align="center"><img src="scrmod-app-icon.png" width="88" alt="SCRMod 应用图标"></p>

<h1 align="center">SCRMod</h1>

<p align="center"><strong>Advanced Control &amp; Diagnostics for Samsung SCR-01</strong></p>

SCRMod 基于 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 提供的临时 Root 权限，为三星 SCR-01（Galaxy 5G Mobile Wi-Fi）带来更多实用功能，包括扩展热点信道、调整设备流量上限、查看蜂窝网络与小区信息，以及补充原设备缺少的短信收发功能。

目前主要适配 **SCR-01 / SM-H412J，Android 11，SCR01KDU1AVK2 固件**。应用也针对横竖屏布局和深色模式进行了适配，方便在不同使用方向和显示模式下操作。

[发布页面](https://github.com/RedoRosetta/SCRMod/releases) · [使用说明](docs/GETTING_STARTED.md) · [Build58 更新记录](docs/RELEASE_BUILD58.md) · [问题反馈](https://github.com/RedoRosetta/SCRMod/issues/new/choose)

> 当前文档对应 1.0 Build58。GitHub 专版安装包见 Release，公开源码范围见下文。

<p align="center">
  <img src="assets/screenshots/01-home-landscape-light.png" width="960" alt="SCRMod 首页：网络、热点、短信状态和流量上限设置">
</p>

## 选择 5 GHz 热点信道

在原有热点功能的基础上，SCRMod 增加了 5 GHz 信道选择，支持 36、40、44、48 等信道。页面还会显示当前频率、信道和频宽，方便确认设置是否生效。也可以开启开机自动应用，省去重复设置的麻烦。

不同固件和运行环境下，可用信道可能有所不同，应用时仍需通过设备与内核的安全检查。

<p align="center">
  <img src="assets/screenshots/02-hotspot-landscape-light.png" width="960" alt="热点页面：当前运行信道与信道选择" loading="lazy">
</p>

## 调整流量上限

原机的流量上限设置不够灵活，SCRMod 提供了更高额度的设置选项。月度和三日流量上限可以分别调整，也可以随时恢复默认值。首页会显示设备实际读取到的上限，方便查看设置结果。

这里调整的是设备本地的流量上限，不会改变 SIM 卡套餐额度或运营商的限速规则。

## 蜂窝网络设置与小区分析

SCRMod 提供蜂窝网络和频段设置的快捷入口，也将当前小区、邻区及信号信息集中在一个页面。可以查看 LTE / NR、频段、PCI 和信号强度，或展开查看更详细的参数，方便了解设备当前的连接情况和周围的信号变化。

小区信息目前用于查看与分析，暂不支持手动切换或锁定小区。

## 短信功能修复

SCRMod 为原设备补充了短信收发支持，针对中国移动、中国联通和中国电信提供对应的兼容方案。包括为当前 SIM 配置网络与短信 APN、IMS 兼容处理，以及短信发送和接收 Hook。页面会分别显示各组件的状态，方便了解运行情况和排查问题。

短信功能依赖三星原生短信 APP，该 APP 并非本项目作者开发。GitHub 专版保留短信功能所需的客户端实现，但不附带三星短信 APP。如需使用短信功能，请联系作者了解兼容 APP 与授权方式。

短信兼容性受固件、SIM、运营商及短信 APP 版本影响。配置完成后仍需实际测试发送和接收，页面显示运行中并不代表短信一定能够正常收发。

## 界面适配

SCRMod 针对横屏、竖屏和深色模式调整了页面布局、文字对齐、卡片间距与颜色。横屏下集中展示状态和参数，竖屏下则重新排列内容，减少文字拥挤，让首页、热点、网络和设置页面保持一致的使用体验。

## 诊断日志与 NAT 检测

设置页可以读取和导出诊断信息，方便查看运行状态、排查故障。网络页提供 NAT 检测，可以查看当前网络出口的 NAT 类型及映射信息，帮助了解设备的网络连接环境。

### 网络检测、小区信息与深色主题

<p align="center">
  <img src="assets/screenshots/03-network-nat-landscape-light.png" width="960" alt="网络页面：蜂窝设置入口与 NAT 检测，公网映射信息已遮盖" loading="lazy">
</p>

<p>下方依次为深色主题的首页、小区信息和设置页面。</p>

<p align="center">
  <img src="assets/screenshots/05-home-portrait-dark.png" width="30%" alt="深色竖屏首页" loading="lazy">
  <img src="assets/screenshots/04-network-cells-portrait-dark.png" width="30%" alt="深色竖屏小区信息" loading="lazy">
  <img src="assets/screenshots/06-settings-portrait-dark.png" width="30%" alt="深色竖屏设置页面" loading="lazy">
</p>

## 下载与安装

1. 先按照 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 项目的说明准备临时 Root 环境。
2. 从 [Build58 Release](https://github.com/RedoRosetta/SCRMod/releases/tag/v1.0-build58) 下载 GitHub 专版 APK，核对校验值后安装。源码压缩包不能直接安装。
3. 打开 SCRMod 并授予 Root 权限，即可按需调整热点、流量上限及网络设置。
4. 如需使用短信功能，请联系作者确认兼容应用与授权方式。不要在公开 Issue 中提交设备码或授权材料。

SCRMod 不包含三星短信 APP 或 Root 提权工具。设备重启后，需要重新确认 Root 是否可用；开机自动应用功能不会自行获取 Root。遇到安装或授权异常时，建议先保留错误提示，不要急于卸载或清除数据，以免丢失授权和本地记录。

详细步骤见[安装与离线授权](docs/GETTING_STARTED.md)。

## 版本说明

Build58 减少了重复的授权资源加载和短信组件状态读取，并进一步调整了热点页面的留白。具体变化、校验值与测试情况见[更新记录](docs/RELEASE_BUILD58.md)。

当前本地完整功能版本尚未完成 GitHub 专版的设备安装与业务验收。历史版本曾出现换卡后发送失败、间歇性发送失败及接收失败等情况，Build58 尚不能确认这些问题在所有环境下都已解决；其他设备和固件也暂不保证兼容。

## 源码与许可

仓库提供了经过 Sol 审核的 Build58 部分功能源码。短信、IMS、注入、授权及 APN 实现不在公开范围。**公开源码无法构建完整功能 APK**，部分界面也与上面的完整应用截图有所不同。详细内容见[源码范围](docs/SOURCE_SCOPE.md)。

自有代码目前尚未授予统一的开源许可证，第三方代码和组件遵循各自的许可证。

## 问题反馈

如果在使用 SCRMod 时遇到问题，欢迎通过 [Issues](https://github.com/RedoRosetta/SCRMod/issues/new/choose) 反馈。请尽量说明应用版本、设备固件、操作步骤和遇到的现象。短信问题可以补充运营商、短信 APP 版本，以及发送和接收各自的测试结果。

请勿公开短信正文、号码、完整 PDU、设备码、授权文件、密钥或完整原始日志。提交截图和诊断信息前，请先检查并脱敏，详见[反馈说明](SECURITY.md)。

## 感谢 SCRoot

感谢 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 项目及贡献者为 SCR-01 提供临时 Root 支持，让 SCRMod 能够实现这些设备功能扩展。SCRMod 与 SCRoot 是两个独立项目。

<a id="support"></a>

## 支持开发

如果 SCRMod 对你有帮助，欢迎自愿支持后续开发。当然，分享使用体验、参与测试和反馈问题，同样是对项目很大的支持。

<table align="center">
<tr><th>支付宝 Alipay</th><th>微信支付 WeChat Pay</th></tr>
<tr><td align="center"><img src="https://raw.githubusercontent.com/RedoRosetta/RightDisplay/HEAD/assets/sponsor/alipay.png" width="220" alt="支付宝支持 SCRMod 二维码"></td><td align="center"><img src="https://raw.githubusercontent.com/RedoRosetta/RightDisplay/HEAD/assets/sponsor/wechat-pay.png" width="220" alt="微信支付支持 SCRMod 二维码"></td></tr>
</table>

无法使用这些支付方式也没有关系，欢迎通过反馈帮助改进。

---

<p align="center"><strong>SCRMod</strong><br>Advanced Control &amp; Diagnostics for Samsung SCR-01<br>Developed by <a href="https://github.com/RedoRosetta">RedoRosetta</a></p>
