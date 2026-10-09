# 第三方清单

| 组件 | 使用范围 | 对应来源 / 许可状态 |
| --- | --- | --- |
| AndroidX / Compose / Material 3 / WorkManager | 公开源码与完整 APK | Google AndroidX，Apache-2.0；构建依赖由 Gradle 声明 |
| Kotlin runtime / coroutines | 公开源码与完整 APK | JetBrains / kotlinx.coroutines，Apache-2.0 |
| ZXing core 3.5.3 | 完整 APK 授权二维码 | Apache-2.0；原项目已有许可证副本 |
| Google Tink 1.7.0 / Gson 2.10.1 | 完整 APK 授权验证 | Apache-2.0；许可证见 LICENSES/Apache-2.0.txt |
| Frida inject 17.9.11 | 完整 APK 短信运行环境 | https://github.com/frida/frida-core ；wxWindows Library Licence 3.1；对应版本许可证见 LICENSES/Frida-core-17.9.11-COPYING.txt |
| frida-java-bridge | 完整 APK 的短信脚本依赖 | https://github.com/frida/frida-java-bridge ；本地 vendor 标为 7.0.13，工程声明为 ^7.0.9，该库采用 LGPL-2.0 WITH WxWindows-exception-3.1；当前缺少脚本 bundle 的完整版本映射 |
| Gradle wrapper | 公开构建入口 | https://github.com/gradle/gradle ，Apache-2.0；通用许可证见 LICENSES/Apache-2.0.txt |
| scr01_idc36.ko | 公开副本和完整 APK 信道支持资产 | 维护者确认是项目自有实现；二进制标记 license=GPL；当前工程缺对应 C 源码，不能宣称模块可复现构建 |

SCRoot 为外部前置项目，未随本项目分发。https://github.com/hackintoanetwork/SCRoot

三星短信 APP 是由用户自行准备的外部依赖，不在公开源码或 APK 中。三星相关名称仅用于说明兼容对象。

自有短信实现不公开并不免除第三方组件许可义务。这份清单不代表许可审查完成；精确的传递依赖 NOTICE、脚本 bundle 版本映射及信道模块 C 源码仍是未完成材料，不宣称第三方许可审查或完整可复现构建已完成。
