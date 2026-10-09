# SCRMod 1.0 (build11)

让 SCR-01 的常用调整更顺手：设置数据使用上限、选择 5 GHz 热点信道，并在需要时修复短信收发功能。

## 这个版本能做什么

- **分别调整月度与 3 天数据上限：** 支持 1–2000 GB，两种周期独立保存，也可一键恢复默认。调整的是设备设置，不改变运营商套餐。
- **选择 5 GHz 热点信道：** 查看实际频段、信道和带宽，选择待应用信道，并可保存为开机自动应用。高频段选项受监管域与设备环境限制。
- **短信功能修复（Beta）：** 按移动、联通、电信选择方案，查看收发状态，管理授权和重新配置；移动方案提供 IMS 兼容控制。
- **排查设备与网络问题：** 查看 Root 和热点状态，运行 NAT 诊断，查看或复制诊断记录。

## 本次界面整理

首页重新安排了状态与数据上限操作；网络、短信和设置页面统一了控件样式，并调整横竖屏布局、对齐及深浅色配色。完整安装包已精简至约 **25.7 MiB**。

## 下载与使用

下载下方 Assets 中的 **SCRMod-1.0-build11-v40-slim-ui-aligned-v9.apk**。Source code.zip / tar.gz 是公开功能版源码，不是安装包，也不包含短信和授权实现。

目标环境：**SCR-01 / SM-H412J、Android 11、SCR01KDU1AVK2**。请先按 [SCRoot](https://github.com/hackintoanetwork/SCRoot) 说明取得临时 Root，并授予 SCRMod 权限。感谢 hackintoanetwork 与 SCRoot 贡献者对这款设备的支持。

短信修复需要另行安装兼容的三星短信 APP 并完成相应授权，三星 APP 不随本发布提供。修复后仍应实际收发确认；兼容性受运营商、APP 版本及设备状态影响。NAT 诊断不承诺 Full Cone 或 NAT A。

## 更新与校验

版本：**1.0 (build11)**，`versionCode 40`。安装包沿用历史 Android debug 证书，尚未切换独立生产证书；同签名版本可覆盖更新保留数据。

SHA-256：

```text
4f919a9f3adc36a25b746452b5cbf253197418e2a310ef8a2b02f83816a2e298
```

[使用与兼容性说明](https://github.com/RedoRosetta/SCRMod#下载与安装) · [源码范围](https://github.com/RedoRosetta/SCRMod/blob/main/docs/SOURCE_SCOPE.md) · [第三方材料及现有缺项](https://github.com/RedoRosetta/SCRMod/blob/main/THIRD_PARTY_NOTICES.md) · [反馈问题](https://github.com/RedoRosetta/SCRMod/issues/new/choose)
