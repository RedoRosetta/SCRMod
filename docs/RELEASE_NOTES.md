# SCRMod 1.0 (build11)

面向 Samsung SCR-01 / SM-H412J、Android 11 的设备控制与诊断版本。

## 本次内容

- 首页整理设备状态与数据上限控制。
- 统一按钮、输入框、选择器的尺寸和圆角，改进横竖屏排列及对齐。
- 调整深浅色配色，补回热点状态图标。
- 保留热点信道、数据上限、NAT 诊断及完整 APK 的短信修复能力。
- APK 使用 R8 和资源压缩，约 25.7 MiB。

## 使用前

先参考 https://github.com/hackintoanetwork/SCRoot 准备临时 Root 和 KernelSU-Next。重启后需要重新确认 Root。感谢 SCRoot 对 SCR-01 的支持；SCRMod 不自带提权流程。

短信功能依赖另行准备的兼容三星短信 APP 及相应授权，三星 APP 不随发布材料分发。

## 源码与安装包

公开源码不含短信修复、IMS、注入资产和授权相关实现。Release 完整 APK 保留这些功能。Source code.zip / tar.gz 不能重建完整 APK。

完整 APK：SCRMod-1.0-build11-v40-slim-ui-aligned-v9.apk

SHA-256：4f919a9f3adc36a25b746452b5cbf253197418e2a310ef8a2b02f83816a2e298

签名：历史 Android debug 证书，非独立生产证书。已有同证书安装可覆盖更新保留数据。

验证：完整 APK 构建和 135 项测试通过，目标设备覆盖安装与启动核对完成。UI 视觉、短信收发和网络功能应分别验收，不将安装成功描述为业务验证通过。

