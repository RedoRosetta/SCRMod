 <p align="center">
  <img src="scrmod-app-icon.png" width="96" alt="SCRMod">
</p>

<h1 align="center">SCRMod</h1>

<p align="center">
  让 Samsung SCR-01 更好用。
</p>

<p align="center">
  <strong>More Control, More Possibilities for Samsung SCR-01.</strong>
</p>

<p align="center">
  <a href="https://github.com/RedoRosetta/SCRMod/releases">下载应用</a> ·
  <a href="docs/GETTING_STARTED.md">使用指南</a> ·
  <a href="docs/RELEASE_BUILD58.md">更新日志</a> ·
  <a href="https://github.com/RedoRosetta/SCRMod/issues/new/choose">反馈问题</a>
</p>

---

SCRMod 是一款专为 **Samsung SCR-01（Galaxy 5G Mobile Wi-Fi）** 开发的设备增强工具。

借助 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 提供的临时 Root 能力，SCRMod 让这台移动路由器拥有更多原本不方便使用、或系统没有提供的功能。

你可以自由调整热点信道和流量上限，查看蜂窝网络与附近小区的信号情况，进行 NAT 检测，还可以通过额外的兼容处理使用短信收发功能。

同时，SCRMod 也重新整理了操作界面，支持横屏、竖屏和深色模式，希望让这些功能不仅能用，也更容易使用。

**当前主要适配：** SCR-01 / SM-H412J · Android 11 · SCR01KDU1AVK2

<p align="center">
  <img src="assets/screenshots/01-home-landscape-light.png" width="960" alt="SCRMod 首页">
</p>

## 让 SCR-01 多一点自由

### 📶 更灵活的 Wi-Fi 热点

不再局限于原有的热点设置。

SCRMod 增加了 **5 GHz 热点信道选择**，可以选择 36、40、44、48 等信道，并直接查看当前使用的频率、信道和频宽。

设置好后，还可以开启开机自动应用，省去重复调整的麻烦。

实际可用信道取决于固件、运行环境及设备内核的安全限制。

<p align="center">
  <img src="assets/screenshots/02-hotspot-landscape-light.png" width="960" alt="SCRMod 热点设置">
</p>

### 📊 不再受限于原来的流量上限

想调整 SCR-01 的流量提醒和限制额度？现在可以直接在首页操作。

SCRMod 支持分别设置**月度与三日流量上限**，也可以随时恢复默认值。设置后会读取设备实际生效的数值，方便确认是否成功。

这里调整的是设备自身的流量上限，不会改变运营商套餐额度或限速规则。

### 📡 看懂身边的蜂窝网络

除了提供蜂窝网络频段设置入口，SCRMod 还加入了更直观的小区信息页面。

你可以查看当前连接的小区、附近搜索到的小区，以及 LTE / 5G NR 的频段、PCI、信号强度等信息。

邻区使用信号条展示，方便直观比较信号强弱；需要更详细的信息时，也可以展开查看专业参数。

目前小区信息主要用于观察和分析，**暂不支持手动锁定或切换小区**。

### 💬 让 SCR-01 也能收发短信

SCRMod 为 SCR-01 补充了短信功能支持，针对中国移动、中国联通和中国电信提供不同的兼容方案。

应用可以为当前 SIM 配置所需的网络与短信 APN，并提供 IMS 兼容处理及短信收发修补。各组件的运行状态也会分别展示，方便了解配置进度和排查问题。

短信功能需要配合兼容的三星短信应用及设备授权使用。三星短信应用并非 SCRMod 的一部分，也不随安装包提供。

如需使用，请联系作者了解兼容应用与授权方式。

> 短信兼容性会受到固件、SIM 卡、运营商和短信应用版本的影响。页面显示运行中代表软件组件状态正常，实际收发仍需要通过真实短信测试确认。

### 🔍 网络检测与诊断

想知道当前移动网络的 NAT 类型，或者设备运行时出现了什么问题？

SCRMod 提供 NAT 检测和统一的诊断日志功能。可以查看网络出口情况、读取运行信息，并在需要时导出日志辅助排查。

NAT 检测仅用于了解网络环境，不会直接改变运营商分配的 NAT 类型。

## 不同方向，一样顺手

SCR-01 不总是横着使用。

因此，SCRMod 为横屏、竖屏和深色模式分别调整了界面布局。状态信息、操作按钮和专业参数会根据屏幕方向重新排列，尽量减少拥挤和不必要的滚动。

<p align="center">
  <img src="assets/screenshots/03-network-nat-landscape-light.png" width="960" alt="网络设置与 NAT 检测">
</p>

<p align="center">
  <img src="assets/screenshots/05-home-portrait-dark.png" width="30%" alt="深色模式首页">
  <img src="assets/screenshots/04-network-cells-portrait-dark.png" width="30%" alt="小区信息">
  <img src="assets/screenshots/06-settings-portrait-dark.png" width="30%" alt="设置页面">
</p>

## 下载与安装

**最新文档版本：1.0 Build58**

前往 [GitHub Releases](https://github.com/RedoRosetta/SCRMod/releases/tag/v1.0-build58) 下载 SCRMod 安装包。

使用前需要准备以下环境：

1. **SCRoot：** 按照 [SCRoot 项目说明](https://github.com/hackintoanetwork/SCRoot) 准备临时 Root 环境。
2. **安装 SCRMod：** 下载对应 APK，核对 SHA-256 校验值后安装。
3. **授予权限：** 启动 SCRMod，授予所需的 Root 权限，即可使用相应的设备增强功能。
4. **短信功能：** 如需启用短信收发，请联系作者了解兼容应用及离线授权方式。

SCRMod 不包含 Root 提权工具，也不包含三星短信应用。设备重启后，可能需要重新建立临时 Root 环境；开机自动应用功能不会自行获取 Root。

如果遇到授权或配置问题，建议先保留错误提示，不要急于清除应用数据，以免丢失授权或本地记录。

更详细的操作步骤请查看 [使用指南](docs/GETTING_STARTED.md)。

## 版本与兼容性

### 1.0 Build58

这一版本主要完善了界面细节与运行效率：

- 优化授权公钥的加载与复用，减少重复解析。
- 合并部分短信组件检查中的重复 Root 读取。
- 调整热点页面布局，减少不必要的留白。

本版通过了 59 项针对性测试。具体更新内容及安装包校验信息见 [Build58 更新记录](docs/RELEASE_BUILD58.md)。

**关于兼容性：**

SCRMod 目前主要面向 SCR-01 的 AVK2 固件开发和测试。其他固件或设备尚未保证兼容。

短信功能曾在历史版本中完成三运营商的实际收发测试，但也出现过换卡后发送失败、间歇性发送失败及接收失败等情况。Build58 尚未在所有使用场景下完成正式实机验收，因此不保证每种网络环境下都能正常收发。

我们会继续根据实际测试与反馈改进兼容性。

## 关于公开源码

SCRMod 在这个仓库中提供了**部分功能源码**，包括经过审核、允许公开的设备增强相关实现。

出于项目维护和实现边界的考虑，短信、IMS、注入、授权以及 APN 等部分实现暂未公开。

因此，**仓库中的公开源码不能直接构建出与 Release 安装包功能完全一致的版本**，部分界面也可能与完整应用截图不同。

详细内容请查看 [公开源码范围](docs/SOURCE_SCOPE.md)。

目前项目自有代码尚未统一授予开源许可证；第三方组件仍遵循各自的许可条款。

## 反馈与交流

如果你在使用 SCRMod 时遇到问题，欢迎通过 [GitHub Issues](https://github.com/RedoRosetta/SCRMod/issues/new/choose) 告诉我们。

反馈时，最好附上应用版本、设备固件、问题出现前的操作，以及实际看到的现象。如果是短信问题，也可以说明运营商、短信应用版本和发送、接收各自的测试结果。

**请保护好自己的隐私。** 不要在公开 Issue 中上传短信正文、电话号码、完整 PDU、设备码、授权文件、密钥或未经检查的原始日志。截图和诊断信息请先脱敏，详见 [安全与反馈说明](SECURITY.md)。

## 致谢

SCRMod 的实现离不开 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 提供的临时 Root 支持。

感谢 SCRoot 项目及其贡献者，让 SCR-01 有了更多探索和扩展的可能。SCRMod 与 SCRoot 是两个独立项目。

也感谢参与测试、提出建议和反馈问题的每一位用户。

<a id="support"></a>

## 支持开发

SCRMod 是一个持续完善中的个人项目。

如果它让你的 SCR-01 变得更好用，欢迎自愿支持后续开发。当然，提交问题反馈、分享使用体验或帮助测试，同样是很有价值的支持。

<table align="center">
  <tr>
    <th>支付宝 Alipay</th>
    <th>微信支付 WeChat Pay</th>
  </tr>
  <tr>
    <td align="center">
      <img src="https://raw.githubusercontent.com/RedoRosetta/RightDisplay/HEAD/assets/sponsor/alipay.png" width="220" alt="支付宝支持二维码">
    </td>
    <td align="center">
      <img src="https://raw.githubusercontent.com/RedoRosetta/RightDisplay/HEAD/assets/sponsor/wechat-pay.png" width="220" alt="微信支付支持二维码">
    </td>
  </tr>
</table>

<p align="center">
  无论是否赞助，都感谢你对 SCRMod 的关注与支持。
</p>

---

<p align="center">
  <strong>SCRMod</strong>
</p>

<p align="center">
  Made for Samsung SCR-01 · Developed by
  <a href="https://github.com/RedoRosetta">RedoRosetta</a>
</p> 
