# Build and test / 编译与测试

The current APK build script targets Windows. Install JDK 17, Android SDK build-tools **36.0.0** and Android platform **34** yourself. Java sources use Android framework APIs, with no Gradle or third-party Android runtime dependencies.

```powershell
python watchroom-tv/build.py --jdk "C:\jdk-17" --sdk "C:\Android\Sdk" --work build/android --output dist/dandan-cinema-0.1.0-preview.1.apk
```

The script creates a fresh signing key and private password file under `runtime/watchroom/` on the first build, then verifies the APK signature. Preserve both privately if you intend to ship compatible updates. Never commit them. Your own signed build cannot update the maintainer's differently signed release. The release package ID is `io.github.erica1018.dandancinema`, minimum API 21, target API 28. Modernizing the target SDK is planned; it is not a Play Store release.

Use a fresh build directory for each clean release; archive the resulting APK and record its SHA-256. Do not delete keys as part of build cleanup. Successful compilation proves neither a launch nor playback on a physical TV.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-mcp.txt
.\.venv\Scripts\python.exe -m unittest discover -s watchroom -p "test_*.py"
python tools/check_release.py
```

Without optional MCP dependencies, that protocol test is explicitly skipped. Release checks must include it. Tests create isolated temporary libraries and do not connect to a TV. CI runs the Python suite on Windows. The original artwork preparation helper additionally requires `python -m pip install Pillow`; it uses the same API and data directory as the application.

中文：当前电视编译脚本使用 Windows、JDK 17、Android SDK 36.0.0 构建工具及平台 34。首次编译会生成自己的签名文件，请私密备份，不要提交。自行签名的版本不能直接覆盖维护者签名的安装包。每次正式构建使用新的工作目录。编译成功不能当作电视实机验收；发布前必须安装 MCP 可选依赖并运行全部测试。
