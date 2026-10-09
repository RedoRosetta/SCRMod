# 构建公开源码版

需要 JDK 17、Android SDK 35、Gradle 8.7。首次构建需要网络获取 Gradle 和 Android 依赖。

Windows:

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
```

macOS / Linux:

```sh
chmod +x gradlew
./gradlew :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
```

构建输出位于 app/build/outputs/apk/release/。该构建不含短信修复或授权模块。

当前示例使用本机生成的 Android debug key，仓库不分发签名私钥。不同电脑生成的证书不同，不能保证覆盖安装官方完整 APK。不要通过卸载完整 APK 来绕过签名冲突，否则可能丢失设置与授权。正式分发应配置自己的签名并保护密钥。

本地公开副本的构建和测试记录是编译验证，不代表真实设备功能验收；完整 APK 的短信行为需要独立验收。
