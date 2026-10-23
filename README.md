<div align="center">

<img src="scrmod-app-icon.png" width="88" alt="SCRMod">

<h1>SCRMod</h1>

<strong>Advanced Control &amp; Diagnostics for Samsung SCR-01</strong>

<p>专为 Samsung SCR-01 开发的设备功能扩展与诊断工具。</p>

<p>
  <a href="https://github.com/RedoRosetta/SCRMod/releases">下载</a>
  ·
  <a href="docs/GETTING_STARTED.md">使用说明</a>
  ·
  <a href="docs/RELEASE_BUILD58.md">更新记录</a>
  ·
  <a href="https://github.com/RedoRosetta/SCRMod/issues/new/choose">问题反馈</a>
</p>

</div>

---

SCRMod 基于 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 提供的临时 Root 能力，为三星 SCR-01（Galaxy 5G Mobile Wi-Fi）补充原系统没有提供、或不便直接使用的功能。

目前包括热点信道设置、设备流量上限调整、蜂窝网络与小区信息查看、NAT 诊断，以及短信收发兼容支持。

**适配设备：** Samsung SCR-01 / SM-H412J  
**系统版本：** Android 11  
**适配固件：** SCR01KDU1AVK2  
**当前版本：** 1.0.5

> 本项目主要针对上述设备和固件开发，其他版本尚未完成兼容性验证。

## 功能介绍

### 首页与流量管理

首页集中显示蜂窝网络、Wi-Fi 热点和短信功能的当前状态，同时提供常用设置入口。

针对原系统的流量限制，SCRMod 提供月度和三日流量上限设置。可以调整到更高的额度，也可以恢复默认值。设置完成后，应用会重新读取设备中的实际配置，避免出现界面显示已应用、实际却没有生效的情况。

需要说明的是，这里修改的是设备自身的流量上限，不涉及运营商套餐、计费或限速策略。

<p align="center">
  <img src="assets/screenshots/01-home-landscape-light.png" width="960" alt="SCRMod 横屏首页">
</p>

### Wi-Fi 热点

SCRMod 扩展了原系统的热点设置，支持选择 5 GHz 工作信道，包括 36、40、44、48 等常用信道。

支持选择 **20 / 40 / 80 MHz** 目标频宽，与信道设置共用“应用”操作。页面显示实际运行的频率、信道和频宽；显式设置频宽时，实际值不符或无法读取，不会只凭信道正确报告成功。

保存的信道和频宽可随原有“开机自动应用”设置生效。另有默认关闭的“热点重开后恢复”选项，在应用观察到热点重新开启时恢复一次，已匹配则不重复切换；没有后台反复强制切宽度循环。可用信道仍受设备、固件及内核限制，不会绕过必要的安全检查。

<p align="center">
  <img src="assets/screenshots/02-hotspot-landscape-light.png" width="960" alt="Wi-Fi 热点设置与信道选择" loading="lazy">
</p>

### 蜂窝网络与 NAT 诊断

网络页面整合了蜂窝网络设置入口与 NAT 检测功能。

可以查看当前网络出口的 NAT 类型、映射地址和端口等信息，用于判断设备所处的网络环境，辅助排查远程连接、端口映射等问题。

NAT 检测是只读诊断，不会修改运营商网络配置，也不能直接改变当前 NAT 类型。

<p align="center">
  <img src="assets/screenshots/03-network-nat-landscape-light.png" width="960" alt="蜂窝网络设置与 NAT 诊断" loading="lazy">
</p>

### 基站与小区信息

SCRMod 可以读取当前服务小区及邻区信息，分别展示 LTE 和 NR 网络的相关参数，包括频段、PCI、频点和信号强度。

为方便比较，页面使用信号条展示各小区的接收信号强度，同时保留详细数值。LTE RSRP 与 NR SS-RSRP 会明确区分，避免混淆不同网络的测量指标。

信号条仅反映参考信号强度，不代表网速或综合信号质量。

目前小区信息以读取和分析为主，**尚未实现手动锁定或切换小区**。

<p align="center">
  <img src="assets/screenshots/04-network-cells-portrait-dark.png" width="420" alt="LTE 与 NR 小区信息" loading="lazy">
</p>

### 短信功能修复

SCR-01 原系统没有提供完整可用的短信收发体验。SCRMod 在保留设备正常移动数据连接的前提下，尝试恢复基于 IMS 的短信功能。

目前针对中国移动、中国联通和中国电信分别提供兼容方案。短信页面包含设备授权、运营商选择、IMS 兼容、短信接收、短信发送，以及运营商 APN 配置入口。

IMS、接收和发送组件的状态独立显示，可以分别查看运行情况，不会因为某一项正常就将整个短信功能标记为完全可用。

<p align="center">
  <img src="assets/screenshots/msm.png" width="420" alt="短信功能：运营商方案、IMS 兼容、短信收发及 APN 配置" loading="lazy">
</p>

短信功能还依赖兼容的三星原生短信 APP。该应用并非 SCRMod 的组成部分，也不随安装包分发。

由于不同运营商、SIM 卡和固件环境存在差异，**组件显示“运行中”不等于短信收发测试通过**。实际可用性需要分别验证发送和接收。

短信功能采用设备绑定的离线授权机制，具体使用方式请参阅安装说明。

### 短信转发

收到新短信后，可按设置转发至指定手机号，支持“全部短信”和“仅验证码”两种模式。默认关闭，启用前需要确认目标并授予系统短信权限。1.0.2 默认不附加来源号码，避免来源数字干扰接收端识别；需要时可手动开启。

转发只处理新收到的短信，不读取历史记录。更换 SIM 或修改设置后需要重新确认状态，结果不明时不会自动重发。验证码识别可能误判，转发可能产生短信费用，请按需开启。

### 界面与显示适配

SCRMod 同时适配横屏、竖屏及系统深色模式。

横屏布局以状态概览和并排操作为主，充分利用 SCR-01 的屏幕宽度；竖屏则重新组织卡片和参数，避免简单缩放横屏界面造成文字拥挤。

深色模式使用独立调整的背景、卡片和文字层级。网络、热点、短信等状态仍保留各自的颜色提示，不会因为切换主题而丢失状态区分。

下图为竖屏深色模式下的首页。与横屏布局相比，状态信息和流量设置会按竖向空间重新排列，功能保持一致。

<p align="center">
  <img src="assets/screenshots/05-home-portrait-dark.png" width="420" alt="竖屏深色模式首页" loading="lazy">
</p>

### 设置与诊断日志

设置页面集中管理后台通知、开机自动应用及其他应用选项。

诊断功能也统一放在这里。需要排查问题时，可以查看或导出相关状态和日志，不必在各功能页面之间寻找诊断入口。

日志导出会对敏感信息进行处理。反馈问题时仍建议检查内容，避免公开号码、短信正文、设备授权信息或其他隐私数据。

<p align="center">
  <img src="assets/screenshots/06-settings-portrait-dark.png" width="420" alt="竖屏深色模式设置页面" loading="lazy">
</p>

---

## 下载与安装

最新版本请前往 [GitHub Releases](https://github.com/RedoRosetta/SCRMod/releases)。

当前发布版本为 **SCRMod 1.0.5**。

安装前请确认：

1. 设备为 Samsung SCR-01，且系统、固件符合当前适配范围。
2. 已按照 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 的说明准备临时 Root 环境。
3. 下载的是 Release 中提供的 APK，而不是 GitHub 自动生成的源码压缩包。
4. 如需使用短信功能，另行确认兼容短信 APP 和授权要求。

SCRMod 不包含 Root 提权工具，也不附带三星短信 APP。

设备重启后，临时 Root 状态可能发生变化，需要重新确认。请勿随意清除应用数据，避免丢失本地授权及相关记录。

完整步骤见 [使用说明](docs/GETTING_STARTED.md)。

## 版本与开发状态

**1.0.5** 主要完善短信组件异常恢复与状态判断，减少暂时无法读取状态时不必要的重建，并精简重复诊断处理。无法确认的状态仍显示待确认，原有安全检查保留，实际资源收益尚未量化。

热点设置改进了目标频宽保存与实际回读，新增默认关闭的“热点重开后恢复”：观察到热点重新开启时恢复一次已保存的信道和频宽，已匹配则不重复切换。没有持续强制切换，也不保证永久保持80 MHz。

完整专版63项、公开副本107项测试及两份优化构建通过，集合有重叠。**1.0.5 已通过用户实机验收。** Phone原生崩溃、内存及长调用问题仍不宣称根治。

详细记录见 [1.0.5 更新说明](docs/RELEASE_1.0.5.md)。现有截图为此前版本，尚未展示新增恢复选项。
## 公开源码说明

本仓库公开 SCRMod 的部分功能源码，主要用于展示设备控制、界面及基础诊断等实现。

短信、IMS、注入、授权和 APN 等模块暂不公开，因此**仓库中的源码不能直接构建出与 Release 相同的完整功能 APK**。

公开范围详见 [SOURCE_SCOPE.md](docs/SOURCE_SCOPE.md)。

目前未对自有代码授予统一的开源许可证。第三方组件仍遵循其各自的许可要求。

## 问题反馈

使用过程中遇到问题，可以通过 [GitHub Issues](https://github.com/RedoRosetta/SCRMod/issues/new/choose) 提交反馈。

建议附上设备固件、SCRMod 版本、复现步骤和实际现象。如果是短信问题，请分别说明运营商、发送结果和接收结果。

请勿公开设备码、授权文件、密钥、短信内容、完整 PDU 或未经脱敏的原始日志。

更多说明见 [SECURITY.md](SECURITY.md)。

## 致谢

感谢 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 项目及贡献者提供的临时 Root 支持。

SCRMod 与 SCRoot 是独立项目。SCRMod 的部分功能需要依赖 SCRoot 提供的运行环境。

## 支持开发

SCRMod 是一个持续维护的个人项目。

如果这个项目对你有帮助，欢迎通过以下方式支持开发。提交问题、分享测试结果或提出改进建议，也同样有帮助。

<table align="center">
<tr>
<th>支付宝 Alipay</th>
<th>微信支付 WeChat Pay</th>
</tr>
<tr>
<td align="center"><img src="assets/sponsor/alipay.png" width="220" alt="支付宝二维码"></td>
<td align="center"><img src="assets/sponsor/wechat-pay.png" width="220" alt="微信支付二维码"></td>
</tr>
</table>

---

<p align="center">
  <strong>SCRMod</strong><br>
  Advanced Control &amp; Diagnostics for Samsung SCR-01<br>
  Developed by <a href="https://github.com/RedoRosetta">RedoRosetta</a>
</p>



