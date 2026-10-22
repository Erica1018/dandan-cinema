# Preview validation / 预览版验证

Release candidate: **0.1.0-preview.1**, 2026-10-22 (UTC+8).

- Windows Python 3.14: 25 automated tests passed, including real stdio MCP initialization, read-only/write tool discovery, writes, deletion confirmation, API CSRF/origin checks, file ownership, revision conflicts, partial-deletion retry, range streaming and artwork boundaries.
- Edge/Chromium automated browser smoke: add a fictional title, search, switch English/Chinese, cancel and confirm deletion; no page errors. Checked a 390px viewport for horizontal overflow. README screenshots come from this temporary demo library.
- Android APK compiled using JDK 17, build-tools 36.0.0, platform 34. APK signing verification passed. No private installation or device was changed.
- Release-tree checks validate Python syntax, required documents, relative documentation links and common private-data/secret patterns. This is a focused release check, not an exhaustive security audit.

**Not yet verified:** public APK installation and playback on a physical TV; a fresh user's full media-conversion/pairing journey; end-to-end Codex/OpenClaw/other host integration; macOS/Linux installation; modern Android compatibility. Historical private-TV evidence does not replace this public package's acceptance.

中文：已通过 25 项自动测试、真实 MCP 协议流程、双语网页交互与窄屏检查，并完成电视包编译和签名验证。仍待验证公开包的实体电视安装播放、陌生用户完整配置过程、各 Agent 客户端端到端接入及其他操作系统。历史私人版本的成功不能代替公开版验收。

CI results are attached to the corresponding GitHub commit. Local checks and hosted CI are separate evidence; a workflow must actually pass before it is described as passing.
