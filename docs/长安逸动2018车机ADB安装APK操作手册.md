# 长安逸动 2018 车机 ADB 安装 APK 操作手册

> 适用对象：本次实际调试的 2018 款长安逸动 Android 4.4.2 原厂车机  
> 文档版本：2026-10-08  
> 说明：本文记录本车已经验证过的安装路径，并把普通 Android 安装与本车的厂商鉴权绕行方式分开说明。

## 1. 先理解三个概念

- `adb push`：只把 APK 复制到车机，并不安装。
- `adb install` / `pm install`：调用 Android Package Manager 正常安装。
- 复制到 `/data/app`：利用本车工程 ADB 对 `/data/app` 的写权限，让 Android 4.4 的 Package Manager 扫描并登记 APK。这是本车在普通安装返回 `INSTALL_FAILED_NOT_AUTH` 后使用的特殊方式。

APK **不需要**放进 `C:\platform-tools`。只要命令里使用 APK 的完整路径即可。把 APK 放在该目录只是为了少输入路径。

## 2. 前置条件

1. 车辆停稳，保持供电稳定；安装过程中不要点火、熄火或拔线。
2. Windows 已准备 ADB，例如：

   ```text
   C:\platform-tools\adb.exe
   ```

3. 使用支持数据传输的 USB-A 对 USB-A 线连接电脑与车机。
4. 在本车工程入口中开启 USB 调试。
5. 需要电脑连接车机调试时，将车机 USB 切到 Device 模式。此前本车使用过的入口为：
   - `*#518200#*`：进入 Android 设置并确认 USB 调试；
   - `*#518121#*`：切换 USB Device 模式。
6. 以上入口只用于本车已验证的工程设置，不应套用到其他长安车型。

ADB 安装完成后，日常使用不需要一直连接电脑。若要使用有线 CarPlay，USB 角色应按车机有线 CarPlay 所需状态恢复；ADB 的 Device 模式与车机作为 USB 主机连接 iPhone 不是同一用途。

## 3. Windows PowerShell 准备

打开 PowerShell，定义 ADB 和 APK 路径：

```powershell
$adb = 'C:\platform-tools\adb.exe'
$apk = 'C:\Users\lzc\Desktop\EadoPlay.apk'
```

检查两个文件都存在：

```powershell
Test-Path -LiteralPath $adb
Test-Path -LiteralPath $apk
```

两条命令都应返回 `True`。

## 4. 确认 ADB 已连接

```powershell
& $adb devices -l
```

正常示例：

```text
List of devices attached
123456789    device
```

不同状态的处理：

- 没有设备：重新插拔数据线，检查 Device 模式和 USB 调试。
- `unauthorized`：查看车机屏幕并允许 USB 调试，建议勾选“始终允许”。
- `offline`：执行以下命令后重新插线：

  ```powershell
  & $adb kill-server
  & $adb start-server
  & $adb devices -l
  ```

确认 Shell 身份：

```powershell
& $adb shell id
```

只有当前车机工程 ADB 确实能够写 `/data/app` 时，才能使用本文第 6 节的特殊安装方式。普通 Android 手机或其他车机不能照搬。

## 5. 第一选择：标准 ADB 安装

先尝试 Android 标准方式：

```powershell
& $adb install -r $apk
```

其中 `-r` 表示保留应用数据并覆盖升级同包名 APK。

成功时会显示：

```text
Success
```

本车曾实际返回：

```text
Failure [INSTALL_FAILED_NOT_AUTH]
```

这表示 APK 已传到车机，但被长安定制 Package Manager 的授权检查拒绝。它通常不是因为 APK 位于 U 盘，也不是把 APK 换到内部存储就自然解决。

遇到其他错误时不要立即使用特殊安装：

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：同包名旧版与新版签名不同；
- `INSTALL_FAILED_OLDER_SDK`：APK 的最低 Android 版本高于 4.4.2；
- `INSTALL_PARSE_FAILED_*`：APK 损坏、签名格式或打包方式不兼容；
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`：内部存储空间不足。

## 6. 本车已验证的特殊安装方式

### 6.1 原理

本车普通安装会被 `INSTALL_FAILED_NOT_AUTH` 拒绝，但工程 ADB Shell 当时能够写 `/data/app`。实际成功路径是：

```text
电脑 APK
→ adb push 到 /data/local/tmp
→ 复制为 /data/app 下的临时文件
→ 设置 system.system、0644
→ 原子改名为正式 APK
→ Android Package Manager 扫描并登记
```

这不是把应用变成 `/system/app` 系统应用，也不需要改 `/system` 分区；但它确实依赖高权限工程 ADB，风险高于标准安装。

### 6.2 安装 EadoPlay

当前 EadoPlay 包名：

```text
com.eadoplay.carplay
```

推送 APK：

```powershell
& $adb push $apk /data/local/tmp/eadoplay-new.apk
```

停止旧版并执行原子替换：

```powershell
& $adb shell "am force-stop com.eadoplay.carplay; cp /data/local/tmp/eadoplay-new.apk /data/app/.eadoplay.apk.tmp && chown system.system /data/app/.eadoplay.apk.tmp && chmod 0644 /data/app/.eadoplay.apk.tmp && mv /data/app/.eadoplay.apk.tmp /data/app/eadoplay.apk && sync"
```

说明：

- 先复制为隐藏临时文件，避免 Package Manager 扫描到只写了一半的 APK；
- `chown system.system` 和 `chmod 0644` 是本车 Android 4.4 已验证的格式；
- 最后的 `mv` 在同一分区内完成原子替换；
- 不要一边运行应用一边直接覆盖正式 APK。

等待约 8 秒，然后验证：

```powershell
Start-Sleep -Seconds 8
& $adb shell "pm list packages -f | grep com.eadoplay.carplay; dumpsys package com.eadoplay.carplay | grep -E 'codePath|versionName|versionCode'"
```

预期至少能看到：

```text
package:/data/app/eadoplay.apk=com.eadoplay.carplay
```

清理推送文件：

```powershell
& $adb shell "rm -f /data/local/tmp/eadoplay-new.apk; sync"
```

启动应用可使用车机桌面图标；也可尝试：

```powershell
& $adb shell monkey -p com.eadoplay.carplay -c android.intent.category.LAUNCHER 1
```

### 6.3 安装 Lite 测试版

Lite 已确认包名为：

```text
com.shilapi.diplay.lite
```

示例：

```powershell
$liteApk = 'C:\Users\lzc\Desktop\Lite-M6k3-release.apk'
& $adb push $liteApk /data/local/tmp/diplaylite-new.apk
& $adb shell "am force-stop com.shilapi.diplay.lite; cp /data/local/tmp/diplaylite-new.apk /data/app/.diplaylite.apk.tmp && chown system.system /data/app/.diplaylite.apk.tmp && chmod 0644 /data/app/.diplaylite.apk.tmp && mv /data/app/.diplaylite.apk.tmp /data/app/diplaylite.apk && sync"
Start-Sleep -Seconds 8
& $adb shell "pm list packages -f | grep com.shilapi.diplay.lite"
& $adb shell "rm -f /data/local/tmp/diplaylite-new.apk; sync"
```

EadoPlay、Lite 和原作者 DiPlay 的包名不同，可以并存；但同时运行多个 CarPlay 接收程序会争用蓝牙、AirPlay 端口、VPN和音频焦点。测试时只保留一个在前台，并强制停止其他版本。

## 7. 更新、签名和数据保留

- 相同包名、相同签名：标准 `adb install -r` 一般能保留数据；本车若被授权检查拦截，则使用同一 `/data/app/文件名.apk` 原子替换。
- 相同包名、不同签名：不要直接覆盖并期待稳定升级。应先导出需要的设置，再卸载或移除旧版后干净安装。
- 不同包名：Android 会视为两个独立应用，设置和数据互不继承。
- 直接替换 `/data/app` APK 通常保留 `/data/data/<包名>`；若新旧版本数据结构不兼容，应用仍可能崩溃，需要清除该应用数据。

安装前建议记录 SHA-256：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
```

这样能够确认车机上测试的究竟是哪一版 APK。

## 8. 安全卸载

优先使用标准卸载：

```powershell
& $adb shell pm uninstall com.eadoplay.carplay
```

如被厂商限制，先查询准确路径：

```powershell
& $adb shell pm path com.eadoplay.carplay
```

只有输出明确为下面这个已知路径时，才执行文件删除：

```text
package:/data/app/eadoplay.apk
```

然后：

```powershell
& $adb shell "am force-stop com.eadoplay.carplay; rm -f /data/app/eadoplay.apk; sync"
```

等待 Package Manager 更新；必要时重启车机。不要使用 `/data/app/*`、`rm -rf /data/app` 或任何宽泛通配符，也不要随意删除 `/data/data`，以免误删其他应用和用户数据。

其他已知包名：

| 应用 | 包名 | 曾使用的 APK 路径 |
| --- | --- | --- |
| EadoPlay | `com.eadoplay.carplay` | `/data/app/eadoplay.apk` |
| Lite | `com.shilapi.diplay.lite` | `/data/app/diplaylite.apk` |
| 原作者 DiPlay | `com.shihab.diplay` | `/data/app/diplay.apk` |

删除前始终以 `pm path <包名>` 的实时输出为准。

## 9. 常用诊断命令

检查应用是否登记：

```powershell
& $adb shell "pm list packages -f | grep -E 'eadoplay|diplay'"
```

查看版本和安装路径：

```powershell
& $adb shell "dumpsys package com.eadoplay.carplay | grep -E 'codePath|versionName|versionCode|firstInstallTime|lastUpdateTime'"
```

只抓 EadoPlay 相关日志：

```powershell
& $adb logcat -c
& $adb logcat -v time | Select-String -Pattern 'EadoPlay|CarPlay|AirPlay|iAP2|MediaCodec|AudioTrack|FATAL EXCEPTION'
```

查看崩溃：

```powershell
& $adb logcat -d -v time | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime|SIGSEGV|MediaCodec'
```

查看剩余空间：

```powershell
& $adb shell df /data
```

## 10. 常见问题判断

### 10.1 `adb devices` 没有车机

这发生在安装之前，与 APK 无关。检查数据线、USB Device 模式、USB 调试授权和 Windows ADB 驱动。

### 10.2 `INSTALL_FAILED_NOT_AUTH`

这是长安厂商鉴权拒绝。把 APK 从 U 盘复制到内部存储通常不会改变结果。本车可以在确认工程 ADB 对 `/data/app` 有写权限后，使用第 6 节的已验证路径。

### 10.3 文件出现在 `/data/app`，但桌面没有图标

依次检查：

```powershell
& $adb shell "ls -l /data/app/eadoplay.apk"
& $adb shell "pm list packages -f | grep com.eadoplay.carplay"
& $adb shell "dumpsys package com.eadoplay.carplay | grep -E 'installStatus|enabled|codePath'"
```

如果只有文件、没有包记录，先等待十秒；仍无记录时重启车机。若仍失败，抓取 PackageManager 日志检查 APK 解析或签名错误，不要反复覆盖。

### 10.4 安装后立即被“非授权应用”卸载

说明厂商安全服务仍在主动处理应用。不要随意长期停止系统安全服务。先记录安全服务包名、进程和完整日志，再决定是否存在可逆、最小影响的临时测试方法。

### 10.5 安装成功但打不开

安装与运行是两个阶段。检查 Android 4.4/API 19 兼容性、ABI 是否包含 `armeabi-v7a`、缺失权限、原生库加载和首次启动崩溃日志。

## 11. 安装完成后的收尾

1. 确认正式包已登记并能从桌面启动；
2. 删除 `/data/local/tmp` 中本次推送的 APK；
3. 保存 APK、SHA-256 和测试结果；
4. 断开电脑前先停止日志抓取；
5. 日常不调试时可关闭 USB 调试；
6. 若要测试有线 CarPlay，按有线模式需要恢复 USB 角色；
7. 不要同时启动 EadoPlay、Lite 和原作者 DiPlay。

## 12. 一句话版流程

```text
开启本车 USB 调试与 Device 模式
→ adb devices 确认 device
→ 先试 adb install -r
→ 若本车返回 INSTALL_FAILED_NOT_AUTH，推送到 /data/local/tmp
→ 以临时文件复制到 /data/app
→ 设置 system.system 与 0644
→ 原子改名
→ 等 Package Manager 扫描
→ 用 pm list packages / dumpsys package 验证
→ 删除临时文件并保存日志
```

