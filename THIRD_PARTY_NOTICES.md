# Third-party notices / 第三方说明

- Python and Android platform APIs are used; their runtimes/SDKs are not bundled in source releases. Their own licenses apply.
- Optional `mcp` Python SDK: [modelcontextprotocol/python-sdk](https://github.com/modelcontextprotocol/python-sdk), MIT. Its installed dependencies retain their individual licenses. Pinning the direct package is not a complete transitive dependency lock.
- Optional Pillow for artwork preparation: [python-pillow/Pillow](https://github.com/python-pillow/Pillow), HPND. Install separately when using `set_artwork.py`.
- FFmpeg and ffprobe are optional external executables. They are not distributed here; the license depends on the build you obtain. Keep the applicable notices when redistributing a build.
- Android APK includes this project's native code, original brand art, and a short synthetic test clip. No film, commercial poster, subtitle collection, vendor application, browser session or provider credential is bundled.
- Product and platform names in integration examples belong to their respective owners; no affiliation is implied.

Python、Android SDK、FFmpeg、Pillow 和 MCP 等第三方项目分别适用自己的许可。源码发布不捆绑它们的运行环境或供应商应用。电视安装包仅包含项目原生代码、原创标志与合成测试短片，不提供影视片源、商业海报或字幕库。接入示例中的产品名不代表双方存在合作关系。
