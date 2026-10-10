# EadoPlay

EadoPlay 是面向 **2018 款长安逸动原厂 Android 4.4.2 车机**适配的 CarPlay 客户端，基于 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)、[DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android) 及其上游 [xcertplay](https://github.com/shilapi/xcertplay) 修改。

当前实车测试版本：`0.2.7-4.4-eado-r28`。

## 当前状态

- 无线 CarPlay 已在目标车机上实车连通，可使用导航、媒体音频和触控。
- 已适配方控音量、播放/暂停、上一曲、下一曲，以及系统电话接听/挂断。
- 音乐与导航支持 20%–100% 独立软件音量；Android 4.4 上直接调节解码后的 PCM，并保留导航播报时自动压低音乐的逻辑。
- 无线连接完成后会隔离原车 HFP/A2DP/AVRCP，避免原车蓝牙反向暂停 CarPlay；熄火再启动导致蓝牙重连时会自动再次处理，退出 CarPlay 后恢复蓝牙配置。
- 已加入长安 Coagent 麦克风占用适配和 Android 4.4 的 16 kHz 采集兼容路径。锁屏状态下系统电话双向、微信来电已实车验证。
- 包含 Android 4.4.2 所需的音频、视频、网络和后台服务兼容处理。
- 提供 30/60 帧、分辨率比例及 SurfaceView/TextureView 等实验设置。
- 连接页面会显示详细阶段与失败节点，便于实车排查。
- 有线 CarPlay 已包含 USB Host 实现，但目标车机目前仍卡在 Android USB 授权阶段，尚未完成端到端验证。
- 已知限制：iPhone 锁屏时，从 CarPlay 主动拨出的微信电话可能无声，点亮手机后恢复；这是当前版本唯一保留的实车通话限制。

## 下载、源码构建与认证材料

Git 源码不包含配件证书、私钥、Android 发布签名或真实手机配对记录。普通源码构建用于开发和审查，不具备独立完成 CarPlay 配件认证所需的运行时身份。

项目 Release 中提供的实车测试 APK 会按照上游项目的公开发布方式，显式注入一套从公开 Carlinkit 固件研究中获得的实验性配件身份。该身份可以被任何 APK 接收者提取；它不是为 EadoPlay 新签发的 Apple/MFi 身份，也不保证未来 iOS 继续接受。详情见 [安全说明](SECURITY.md)、[构建说明](docs/BUILD.md) 和 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。

源码构建：

```powershell
.\gradlew.bat :mobile:assembleDebug
```

若要构建可独立连接 iPhone 的测试 APK，需在 Git 仓库之外自行准备认证资产，并通过 `DIPLAY_AUTH_ASSETS_DIR` 显式注入。不要把这些文件提交到 Git。

## 车机安装

目标车机的厂商固件会拒绝普通第三方 APK 安装，实车使用了工程 ADB 将 APK 写入 `/data/app` 的设备特定方式：

- [长安逸动 2018 车机 ADB 安装 APK 操作手册](docs/长安逸动2018车机ADB安装APK操作手册.md)
- [EadoPlay 项目说明](docs/EadoPlay项目说明.md)

该方法依赖目标车机开放的工程调试权限，不保证适用于其他车辆。操作前应备份应用数据，禁止盲目修改其他系统文件。

## 免责声明

- 本项目是独立的社区研究和兼容性实验，不是 Apple、长安汽车、Carlinkit 或上游作者的官方产品，也不代表上述主体的认可或合作。
- CarPlay、Apple 及相关标志是 Apple Inc. 的商标或资产；长安及车型名称归各自权利人所有。项目中的名称仅用于说明兼容目标。
- 实验性认证数据的来源、权利状态、持续有效性和一般分发适用性均不作保证。使用者应自行确认当地法律、协议和设备保修要求。
- 本软件按现状提供，不承诺适销性、特定用途适用性、稳定性或普遍兼容性。安装、使用、改装车辆系统造成的风险由使用者自行承担。
- 禁止以本项目冒充官方产品、认证产品或进行误导性商业宣传。分发修改版时必须保留相应开源许可证、来源、版权和第三方声明。
- 实车操作必须在安全停车状态下进行，不要在驾驶过程中安装、调整设置或查看日志。

## 开源许可与致谢

核心接收端继承自 xcertplay/DiPlay，适用 GNU GPL v3；部分界面源自 DiAuto，适用 AGPL v3。完整文本和第三方许可位于 [LICENSE](LICENSE)、`docs/licenses` 与 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。

感谢 xcertplay、DiPlay、DiPlay-Legacy-Android、DiAuto、LIVI、Showcase 及相关开源贡献者。本仓库的独立名称和长安逸动适配不改变上游作者对原始工作的权利。
