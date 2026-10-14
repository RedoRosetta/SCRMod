# SCRMod 1.0 Build58

基于 SCRoot 临时 Root，为三星 SCR-01 扩展热点信道、设备流量上限、蜂窝设置和小区信息分析，并提供短信修复与诊断功能。主要适配 Android 11 / SCR01KDU1AVK2。

- 减少重复授权资源加载与短信组件读取，调整热点页面留白。
- 改善横竖屏布局和深色模式显示（截至 Build58 的累计更新）。
- GitHub 专版保留短信、IMS、APN 和客户端离线授权，移除归档实验内容。

## 下载

普通用户下载 `SCRMod-1.0-build58-GitHub.apk`，版本 1.0 Build58 / 89，包名 `com.scr01.mod`。沿用原签名，可覆盖同签名版本并保留数据。

`SCRMod-public-source-build58.zip` 是93文件的部分功能源码，不含短信、IMS、注入、授权和 APN 实现，无法重建完整 APK。GitHub 自动生成的源码附件同样仅对应公开范围。

APK SHA-256：

```text
e87915e330b42c2f32d9160168f7bec96025d4aa74dee592bc21559874839c17
```

源码包 SHA-256：

```text
243c2ec25760326702dc0a2d8b9fb629f4dc8f4a54ed25128e945fd5e4a110ab
```

短信功能需要非本作者开发的兼容三星原生短信 APP 及设备授权；三星 APP 不随包提供，使用方式请联系作者。

## 验证范围

两份构建通过，公开源码版92项测试通过，APK签名与对齐检查通过。此 GitHub 专版尚未完成设备安装及短信/APN/网络/长待机验收，历史版本的业务测试不扩展为本版三运营商全面通过。实际资源收益未测量，所有历史短信问题并未被证明彻底解决。

自有代码未新增统一开源许可，第三方保留各自许可。完整第三方合规及部分组件精确版本映射仍待核查。

[使用说明](https://github.com/RedoRosetta/SCRMod/blob/main/docs/GETTING_STARTED.md) · [问题反馈](https://github.com/RedoRosetta/SCRMod/issues/new/choose)
