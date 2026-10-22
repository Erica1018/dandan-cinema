# 安装蛋蛋影院

[English](INSTALL.md) · [项目首页](../README.zh-CN.md)

## 1. 先使用电脑片单

1. 安装 [Python 3.11 或以上版本](https://www.python.org/downloads/windows/)。安装时勾选加入 PATH 的选项，完成后重新打开终端。
2. 从[发布页面](https://github.com/Erica1018/dandan-cinema/releases)下载源码 ZIP。解压到准备长期保留的位置，例如 `C:\dandan-cinema`。不要在压缩包内直接运行。
3. 在文件资源管理器中打开解压文件夹，右键空白处，选择“在终端中打开”。输入 `python --version`，确认能看到版本号。
4. 输入 `python run.py`；也可以双击 `Start.cmd`。
5. 浏览器打开 `http://127.0.0.1:18776`，右上角选“简体中文”，点击“收下一个想看”。只填片名也能保存。

使用电视时，电脑及服务需要保持运行。在终端按 Ctrl+C 停止。片库保存在 `runtime/watchroom/`，重新启动不会清空。备份时先停止服务，再复制该目录到私人位置。网页“导出片单”只导出资料，不包含视频，目前没有网页导入功能。

基础片单不需要额外安装 Python 包；MCP 和封面准备另有可选依赖。

## 2. 可选：连接 Android TV

预览版使用开发者模式安装，暂时没有应用商店安装流程。声明最低 Android 5；较新的电视可能拒绝旧目标 SDK 的安装包，请遵守设备的安全策略。安装成功不等于实际播放已经验证。

1. 让电脑与电视接入同一可信家庭网络。在 Windows“设置 → 网络和 Internet → 当前连接 → 属性”找到电脑 IPv4 地址；在电视网络设置中找到电视地址。可以在路由器中为两台设备保留地址，避免重启后变化。
2. 在终端按 Ctrl+C 停止服务。输入下面命令，**把示例地址换成你自己的电脑、电视地址**：

   ```powershell
   python setup_tv.py --host 192.168.1.20 --tv-host 192.168.1.30
   ```

   程序会生成随机配对凭据，保存在 `runtime/watchroom/tv-access.json`，不要分享这个文件。它不会自行扫描或修改设备。
3. 再运行 `python run.py`。Windows 若弹出防火墙提示，仅允许私人网络。如果仍被受管理的防火墙阻挡，请让管理员只允许电视 IP 访问电脑 TCP **18777**。不要在路由器开放公网端口。
4. 下载发布页中的 APK，在电脑安装 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)。按照电视厂商说明开启开发者与网络调试；本项目不会替你修改这些设置。
5. 查看电视显示的 ADB 地址与端口。部分电视使用 `5555`；较新的无线调试可能要求先执行 `adb pair`，连接端口也不同，请按电视提示完成。
6. 替换下面的工具路径、安装包路径和电视地址后执行。电视若弹出“允许这台电脑调试”，请选择允许；第一次因等待授权而退出时，授权后重试。

   ```powershell
   python watchroom-tv/install.py --adb "C:\platform-tools\adb.exe" --apk "C:\Downloads\dandan-cinema-0.1.0-preview.1.apk" --device 192.168.1.30:5555
   ```

   它只针对刚才配置的电视，安装独立的公开版应用，并传入配对信息，不打印凭据，也不删除其他影院应用。
7. 在电视确认片单已出现，进入“片单工具 → 电脑播放测试”，检查播放、暂停、跳转与返回。反馈时说明电视型号、Android 版本和实际结果，不附 IP 或配对凭据。安装完成后可关闭网络调试，正常播放使用影院服务端口。

电视端首次跟随系统语言；在“片单工具 → 中文 / English”可以切换。网络地址变化时，需要停止服务后谨慎替换配对配置。若改配另一台电脑，先在电视设置中仅清除本应用的数据，再重新配对；这会清除电视端续播位置和缓存，不会清空电脑片库。

## 3. 准备自己的本地视频

平台或网盘页面是来源记录；视频直链必须指向媒体本身，也可能受登录、有效期、格式和网络限制。要在电视播放本地文件：

1. 自行取得 [FFmpeg 与 ffprobe](https://ffmpeg.org/download.html)，放在同一个目录。本项目不附带或下载它们。
2. 网页添加片名。运行 `python watchroom/agent.py list`，找到该条目的 `id`。
3. 输入自己的视频文件位置；有字幕时可附 UTF-8 编码的 SRT：

   ```powershell
   python watchroom/prepare_media.py "C:\Videos\my-video.mp4" --ffmpeg "C:\ffmpeg\bin\ffmpeg.exe" --zh "C:\Videos\zh.srt" --en "C:\Videos\en.srt"
   ```

   缺少哪种字幕，就去掉对应参数。`--audio 0` 默认选择第一条音轨，需要自己核对语言。程序会复制或转换视频到管理目录，输出 `asset` 编号；需要足够磁盘空间，此时还没有标记为已核验。
4. 实际检查作品身份、开头结尾及完整性。把 `examples/evidence.json` 复制到 `runtime/evidence.json`，用记事本打开副本。完成检查后才将 `identity` 和 `complete` 改为 `true`，并把 `evidence` 替换为真实核验依据；其他未核实的项保持 `false`。替换下面的条目 ID、媒体编号，再提交这份文件：

   ```powershell
   python watchroom/agent.py publish ITEM_ID --asset ASSET_ID --evidence runtime/evidence.json
   ```

   这条命令记录你的身份与完整性核验声明，不会替你验证内容。没有检查就不要执行；也不要把它当作音轨、字幕已通过验证的证明。MCP 中可分别记录可选的音轨与字幕核验结果。
5. 在电视刷新片单，实际播放该影片，并测试跳到较后的位置。测试短片成功，不代表所有影片都能成功。

删除时会先预览卡片及关联文件数量、占用，确认后再删除。其他内容共用的文件保留；管理目录外的原文件不会被自动登记删除，平台及网盘原文件不会删除。测试媒体工作流前请保留原片备份。

## 常见排错

- **找不到 Python：**安装后重新开终端，检查是否勾选 PATH。
- **端口被占用：**关闭之前的服务。另一实例可先设置 `$env:DANDAN_PORT='18876'` 与 `$env:DANDAN_DATA_DIR='C:\dandan-data-two'`。准备媒体和配对时使用同一数据目录，CLI/MCP 的 `DANDAN_URL` 指向新端口。不要同时让多个服务写同一个目录。
- **电视离线：**检查电脑未休眠、两台设备 IP 未变、防火墙允许，以及服务仍在运行。电视显示缓存片单不代表实时连接成功。
- **能安装但不能播放：**先跑测试短片，再检查视频编码、直链有效期及资源准备步骤。预览版没有 DRM 或供应商登录适配器。
- **MCP 连不上：**先启动 `run.py`，使用虚拟环境 Python 的绝对路径，参考 [Agent 接入](AGENTS.md)。
