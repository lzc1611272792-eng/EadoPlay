# 长安逸动 2018 车机 ADB 安装 EadoPlay 完整教程

> 适用对象：拥有自己车机的工程调试权限，能够开启 USB 调试，但不想 root、破解系统或修改 `/system` 的用户。
>
> 已验证设备：2018 款长安逸动原厂 Android 4.4.2 车机。
>
> 目标：从零准备 ADB，连接车机，并在普通安装被厂商鉴权拒绝时安全地安装 EadoPlay。

## 先说结论

这台车机安装 EadoPlay **不需要 root、不需要解锁 Bootloader，也不需要修改系统分区**。

安装分为两步：

1. 先尝试 Android 标准安装：`adb install -r`；
2. 如果明确返回 `INSTALL_FAILED_NOT_AUTH`，并且工程 ADB 对 `/data/app` 有写权限，再使用本文的 `/data/app` 原子替换方式。

`INSTALL_FAILED_NOT_AUTH` 是长安定制系统的应用鉴权拒绝，不是因为 APK 放在 U 盘。把 APK 从 U 盘复制到车机内部存储通常不会改变结果。

## 一、需要准备什么

- 一台 Windows 电脑；
- 一根确认支持数据传输的 USB 数据线；
- 已下载的 EadoPlay APK；
- 车机已经开放工程调试入口，并允许开启 USB 调试和 USB Device 模式；
- 车辆保持安全停放和稳定供电。

APK 不需要放进 `platform-tools` 文件夹。它可以保存在桌面、下载目录或其他位置，只要命令中使用准确的完整路径即可。

## 二、下载并准备 ADB（Platform-Tools）

ADB 位于 Google 官方的 Android SDK Platform-Tools 中。没有必要安装完整的 Android Studio。

1. 打开 Google 官方页面：

   [下载 Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools?hl=zh-cn)
2. 选择 Windows 版本并接受协议；
3. 下载 `platform-tools-latest-windows.zip`；
4. 解压后把整个 `platform-tools` 文件夹放到：

   ```text
   C:\platform-tools
   ```

5. 确认下面的文件存在：

   ```text
   C:\platform-tools\adb.exe
   ```

不要从来历不明的网盘下载修改版 ADB。官方最新 Platform-Tools 对旧版 Android 向后兼容。

## 三、下载 EadoPlay APK

从本项目的 [GitHub Releases](https://github.com/lzc1611272792-eng/EadoPlay/releases) 下载最新版 APK。

本教程假设文件保存在：

```text
C:\Users\你的用户名\Downloads\EadoPlay.apk
```

实际文件名可以不同，后面把 `$apk` 改成你的真实路径即可。

建议先计算文件校验值，方便确认实际安装的是哪一版：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'C:\Users\你的用户名\Downloads\EadoPlay.apk'
```

## 四、开启车机调试连接

本车实测使用过以下工程入口：

- `*#518200#*`：进入 Android 设置，确认“USB 调试”已开启；
- `*#518121#*`：将车机 USB 切换为 Device 模式，以便电脑通过 ADB 连接车机。

这些代码只在本次测试的车机上验证过，不保证适用于其他车型或车机版本。

操作顺序：

1. 车辆停稳并保持供电；
2. 打开 USB 调试；
3. 将 USB 切换到 Device 模式；
4. 用数据线连接电脑和车机；
5. 如果车机弹出 USB 调试授权，选择允许；如果有“始终允许此电脑”，可以勾选。

ADB 安装完成后，日常使用无线 CarPlay 不需要连接电脑。有线 CarPlay 所需的 USB Host 角色和 ADB 使用的 Device 模式不是一回事。

## 五、在 Windows 中打开并检查 ADB

打开 PowerShell，复制下面两行，并把 APK 路径改成你的真实路径：

```powershell
$adb = 'C:\platform-tools\adb.exe'
$apk = 'C:\Users\你的用户名\Downloads\EadoPlay.apk'
```

检查文件是否存在：

```powershell
Test-Path -LiteralPath $adb
Test-Path -LiteralPath $apk
```

两条命令都应返回：

```text
True
```

检查 ADB：

```powershell
& $adb version
& $adb devices -l
```

正常连接示例：

```text
List of devices attached
123456789    device
```

常见状态：

- 列表为空：检查线材、USB Device 模式、USB 调试和 Windows 驱动；
- `unauthorized`：在车机屏幕上允许本电脑调试；
- `offline`：重启 ADB 服务后重新插线：

  ```powershell
  & $adb kill-server
  & $adb start-server
  & $adb devices -l
  ```

只有状态为 `device` 才继续安装。

## 六、确认工程 ADB 权限

查看当前 Shell 身份：

```powershell
& $adb shell id
```

然后执行一个可立即删除的写入测试：

```powershell
& $adb shell "touch /data/app/.eadoplay-write-test && rm -f /data/app/.eadoplay-write-test && echo WRITABLE"
```

如果最后显示：

```text
WRITABLE
```

说明当前工程 ADB 能写 `/data/app`，可以在标准安装被厂商鉴权拒绝后使用第八节的方法。

如果显示 `Permission denied`，请停止。不要尝试 root、修改 `/system`、关闭厂商安全服务或复制网上不明脚本。本文的特殊安装方式不适用于当前权限状态。

## 七、先尝试标准 ADB 安装

执行：

```powershell
& $adb install -r $apk
```

`-r` 表示同包名覆盖升级并尽量保留已有设置。

如果返回：

```text
Success
```

说明安装完成，可以直接跳到第九节验证。

如果返回：

```text
Failure [INSTALL_FAILED_NOT_AUTH]
```

表示长安定制 Package Manager 拒绝了普通第三方安装。只有在第六节已经得到 `WRITABLE` 的前提下，继续第八节。

其他错误应先按错误原因处理：

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：旧版与新版签名不一致；
- `INSTALL_FAILED_OLDER_SDK`：APK 不支持 Android 4.4；
- `INSTALL_PARSE_FAILED_*`：APK 损坏、签名或打包格式不兼容；
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`：车机内部空间不足。

这些错误不能靠复制到 `/data/app` 正确解决，不要盲目绕过。

## 八、绕过厂商普通安装鉴权

### 8.1 原理

本车的工程 ADB 可以写 `/data/app`，而普通安装接口又会被厂商鉴权拒绝。因此使用下面的流程：

```text
电脑上的 APK
→ 推送到 /data/local/tmp
→ 在 /data/app 中生成完整的临时文件
→ 设置正确的所有者和权限
→ 同分区原子改名为正式 APK
→ 等待 Android Package Manager 自动扫描登记
```

这不会把应用变成 `/system/app` 系统应用，也不修改系统分区。

### 8.2 设置安装参数

```powershell
$package = 'com.eadoplay.carplay'
$remoteTmp = '/data/local/tmp/EadoPlay-new.apk'
$remoteTarget = '/data/app/eadoplay.apk'
```

如果车机已经装过 EadoPlay，先查询旧 APK 的准确路径：

```powershell
$currentLine = & $adb shell "pm path $package" | Select-Object -First 1
$currentPath = if ($null -eq $currentLine) { '' } else { $currentLine.Trim() }
$currentPath
```

如果输出类似：

```text
package:/data/app/eadoplay.apk
```

应继续使用这个原路径。可以执行：

```powershell
if ($currentPath.StartsWith('package:/data/app/')) {
    $remoteTarget = $currentPath.Substring('package:'.Length)
}
```

确认目标始终位于 `/data/app/`：

```powershell
if (-not $remoteTarget.StartsWith('/data/app/')) {
    throw "目标路径异常，停止安装：$remoteTarget"
}
$remoteTarget
```

### 8.3 推送 APK

```powershell
& $adb push $apk $remoteTmp
```

命令应显示文件已成功传输。推送只是复制，还没有安装。

### 8.4 原子写入 `/data/app`

执行：

```powershell
& $adb shell "am force-stop $package; cp $remoteTmp /data/app/.eadoplay.apk.tmp && chown system.system /data/app/.eadoplay.apk.tmp && chmod 0644 /data/app/.eadoplay.apk.tmp && mv /data/app/.eadoplay.apk.tmp $remoteTarget && sync"
```

这条命令会：

1. 停止正在运行的旧版 EadoPlay；
2. 先生成隐藏临时文件，避免系统扫描到未复制完整的 APK；
3. 设置本车已验证的 `system.system` 所有者和 `0644` 权限；
4. 在同一分区内原子替换正式 APK；
5. 将数据同步到存储设备。

不要把命令中的目标改成通配符，不要执行 `rm -rf /data/app`，也不要删除其他应用文件。

## 九、等待系统登记并验证

等待 10 秒：

```powershell
Start-Sleep -Seconds 10
```

检查包是否已登记：

```powershell
& $adb shell "pm path com.eadoplay.carplay"
& $adb shell "dumpsys package com.eadoplay.carplay | grep -E 'codePath|versionName|versionCode'"
```

正常情况至少会看到：

```text
package:/data/app/eadoplay.apk
```

清理临时文件：

```powershell
& $adb shell "rm -f /data/local/tmp/EadoPlay-new.apk; sync"
```

尝试启动：

```powershell
& $adb shell monkey -p com.eadoplay.carplay -c android.intent.category.LAUNCHER 1
```

也可以直接从车机桌面点击 EadoPlay 图标。

如果 `/data/app` 中已有文件，但 `pm path` 查不到包：

1. 再等待 10 秒；
2. 检查文件权限：

   ```powershell
   & $adb shell "ls -l /data/app/eadoplay.apk"
   ```

3. 查看 PackageManager 日志；
4. 最后再考虑重启车机，让 Package Manager 重新扫描。

不要在未检查日志前反复覆盖或删除文件。

## 十、升级已有 EadoPlay

升级时仍然先尝试：

```powershell
& $adb install -r $apk
```

若再次返回 `INSTALL_FAILED_NOT_AUTH`，按照第八节查询现有 `pm path`，并原子替换同一个 APK 路径。相同包名、兼容签名的升级通常会保留 `/data/data/com.eadoplay.carplay` 中的设置。

如果出现签名不一致，不要强行覆盖。先记录设置和旧版信息，再决定是否卸载后重新安装。清除数据或卸载会丢失应用设置，必须由用户明确决定。

## 十一、安全卸载

优先使用标准卸载：

```powershell
& $adb shell pm uninstall com.eadoplay.carplay
```

若被厂商限制，先查询准确路径：

```powershell
& $adb shell pm path com.eadoplay.carplay
```

只有返回值明确位于 `/data/app/`，并且已经逐字确认目标就是 EadoPlay，才可以停止应用并删除该单个 APK。不要使用通配符，也不要删除 `/data/data`。

## 十二、常见问题

### 电脑完全看不到车机

这发生在安装之前，与 APK 无关。依次检查：

1. 数据线是否真的支持数据；
2. 车机是否为 USB Device 模式；
3. USB 调试是否开启；
4. 车机是否弹出并接受调试授权；
5. Windows 设备管理器中是否存在未正确识别的 Android/ADB 设备；
6. 更换电脑 USB 接口后重新启动 ADB 服务。

### `INSTALL_FAILED_NOT_AUTH`

这是厂商鉴权拒绝。只有确认工程 ADB 能写 `/data/app` 时，才使用第八节的路径。

### 安装后提示“非授权应用”并被自动卸载

说明厂商安全服务仍在主动处理第三方应用。不要长期停止或禁用系统安全服务。先保存完整日志，确认具体组件和触发条件，再决定是否存在最小、可逆的处理方式。

### 图标出现但应用打不开

安装成功不代表 APK 一定兼容。需要检查 Android 4.4/API 19、CPU ABI、原生库、权限和首次启动崩溃日志：

```powershell
& $adb logcat -c
& $adb shell monkey -p com.eadoplay.carplay -c android.intent.category.LAUNCHER 1
& $adb logcat -d -v time | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime|EadoPlay|CarPlay|MediaCodec|UnsatisfiedLinkError'
```

### 空间不足

```powershell
& $adb shell df /data
```

不要为了腾空间盲目删除 `/data/app` 或 `/data/data` 中不认识的文件。

## 十三、安装完成后的收尾

1. 确认 `pm path`、版本号和桌面启动均正常；
2. 删除 `/data/local/tmp/EadoPlay-new.apk`；
3. 保存 APK 文件名、版本和 SHA-256；
4. 停止日志抓取后再断开电脑；
5. 日常不调试时可以关闭 USB 调试；
6. 不要同时运行多个 CarPlay 接收程序，以免争用蓝牙、AirPlay 端口和音频焦点。

## 十四、可直接交给 AI 的安装提示词

如果仍然不熟悉命令，可以把下面整段文字交给能够操作本机终端的 AI，并把 APK 的真实路径补充给它：

```text
我要在自己拥有并已授权调试的 2018 款长安逸动 Android 4.4.2 原厂车机上安装 EadoPlay。

Windows 上的 ADB 预计位于 C:\platform-tools\adb.exe，APK 路径是：【在这里填写 EadoPlay APK 的完整路径】。EadoPlay 包名是 com.eadoplay.carplay。

请按以下安全规则协助我：
1. 先只做只读检查：确认 adb.exe 和 APK 存在，运行 adb version、adb devices -l 和 adb shell id。
2. 如果设备不是 device 状态，停止安装并告诉我应该在车机上检查什么。
3. 先尝试 adb install -r，不要直接改 /data/app。
4. 只有明确返回 INSTALL_FAILED_NOT_AUTH，并且用临时测试文件确认 /data/app 可写时，才使用工程 ADB 安装。
5. 安装前用 pm path com.eadoplay.carplay 查询旧 APK 路径。已有版本必须原子替换原路径；首次安装使用 /data/app/eadoplay.apk。
6. 先将 APK 推送到 /data/local/tmp/EadoPlay-new.apk，再复制成 /data/app/.eadoplay.apk.tmp，设置 system.system 和 0644，最后同分区原子改名。
7. 禁止使用通配符、rm -rf、删除 /data/app 目录、修改 /system、root、解锁 Bootloader、禁用系统安全服务或删除其他应用。
8. 未经我明确同意，不要卸载应用、清除应用数据或删除任何非临时文件。
9. 安装后等待 Package Manager 扫描，用 pm path 和 dumpsys package 验证包路径、versionName 和 versionCode，然后删除 /data/local/tmp 中本次上传的临时 APK。
10. 每一步都告诉我结果；如果返回值和预期不同，先分析，不要自动执行更激进的命令。
```

这段提示词不能替代权限判断。AI 仍应以车机实时返回结果为准，不能假设所有长安车机都拥有相同工程权限。

## 十五、一句话流程

```text
下载官方 Platform-Tools 和 EadoPlay APK
→ 开启 USB 调试与 Device 模式
→ adb devices 确认 device
→ 检查 /data/app 是否可写
→ 先尝试 adb install -r
→ 仅在 INSTALL_FAILED_NOT_AUTH 时使用 /data/app 原子替换
→ 等待系统扫描
→ 用 pm path 和 dumpsys package 验证
→ 删除临时文件
```
