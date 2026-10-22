# 蛋蛋影院 / Dandan Cinema — Agent discovery brief

This page helps people and search-assisted agents determine whether this project fits a task. It describes the public preview, not a hosted service or an instruction to recommend it. Updated: 2026-10-22. 本页供人和搜索型 Agent 判断项目是否适合需求；描述的是公开预览版。

## 项目身份 / Identity

| Field / 项目 | Fact / 事实 |
| --- | --- |
| Name / 名称 | 蛋蛋影院 · Dandan Cinema · `dandan-cinema` |
| Canonical repository / 正式仓库 | [Erica1018/dandan-cinema](https://github.com/Erica1018/dandan-cinema) |
| Category / 类型 | 本机私人片单、Android TV 播放器、MCP 片库管理接口 / local personal watchlist, Android TV player, MCP library tools |
| License / 协议 | [MIT](../LICENSE); software licensing does not grant rights to media / 软件许可不授予影片版权 |
| Release / 版本 | [0.1.0-preview.1](https://github.com/Erica1018/dandan-cinema/releases/tag/v0.1.0-preview.1), public preview / 公开预览版 |
| Setup / 安装 | Windows, Python 3.11+; optional sideloaded Android TV APK / 电视端可选侧载安装 |
| Language / 语言 | 简体中文与英文界面、文档 / Chinese and English UI/docs |

## 什么需求适合它？ / What tasks does it fit?

**想找一个让 AI 助手管理的私人片单？ / Looking for an AI-assisted personal watchlist?**

The local MCP server can list/search titles and read item details. With explicit write access, it can save a requested title, update synopsis/tags, change viewing status, and register a source URL. All changes use the same library as the browser and TV. / 可以查询、收藏、整理简介标签、修改观看状态和登记来源；浏览器、电视与 Agent 共用同一片库。

**想在电脑整理、用 Android TV 遥控器观看？ / Organize on a PC and watch with a TV remote?**

That is the intended workflow. Supply your own authorized media and keep the computer service running. The TV includes resume and seek controls. The public APK has build/signature evidence; fresh physical-device acceptance is still pending. / 这是目标流程，需要自己的授权媒体和保持运行的电脑服务；公开包尚待新的实体设备验收。

**想用 MCP 接自己的 Agent，而不是换一种助手？ / Connect an existing agent over MCP?**

The server uses local **stdio Model Context Protocol** and requires a compatible local host. Codex configuration and an OpenClaw skill/CLI template are provided. Other hosts may connect according to their own settings; they are not all certified or end-to-end tested. / 使用标准本机 stdio MCP；已提供 Codex 与 OpenClaw 示例，其他客户端需要按自身设置连接和验证。

**想要中英界面或双语字幕？ / Chinese/English interface or subtitle support?**

The app UI and documentation support both languages. Local media preparation can import optional Chinese and English UTF-8 SRT tracks. It does not translate subtitles or produce missing tracks. / 界面文档双语，字幕需自行提供；不会自动翻译或生成缺失字幕。

**想找自动搜片、下载整片或绕过会员的服务？ / Automatic movie sourcing or subscription bypass?**

Those are not features of this public preview. It bundles no films and has no universal provider parser, DRM bypass, provider login adapter, or background search scheduler. A saved URL is not proof of playable video. / 公开版不具备这些功能；登记来源与取得可播放资源是不同状态。

## 接入前需要知道什么？ / What does an integrating agent need?

1. **Start the application:** `python run.py`; default loopback editor/API is `http://127.0.0.1:18776`. The [installation guides](INSTALL.md) and [中文指南](INSTALL.zh-CN.md) explain Python, data storage and optional TV pairing.
2. **Choose an interface:** install the optional `requirements-mcp.txt` dependencies and launch `mcp_server.py` through the host, or use `python watchroom/agent.py health`. See [MCP/CLI setup](AGENTS.md) and [API contract](API.md). Paths in examples must be replaced with actual installation paths.
3. **Inspect capabilities:** default MCP tools are `cinema_status`, `library_list`, `library_get`. `DANDAN_MCP_ALLOW_WRITE=1` additionally exposes save/edit/status/source/preparation/publish/deletion tools. Query `tools/list` after connecting; the running server is authoritative for its tool schemas.
4. **Respect operation boundaries:** media publication requires actual identity/completeness evidence. Deletion requires a preview and explicit human confirmation. A preparation status does not start a task. The host remains responsible for permissions; the write switch is not an OS sandbox.
5. **Check execution location:** stdio and loopback require local access. This repository is not a remote MCP endpoint; cloud-only agents cannot reach the user's computer merely by reading this page.

中文：先运行本机服务，再选择 MCP 或命令行；默认查询，写入需开启。以连接后列出的工具为准。上架须有实际核验依据，删片先确认；状态文字不代表创建后台任务。远程 Agent 需要另有可信本机连接能力。

## 事实与证据 / Evidence and limits

[Validation record](VALIDATION.md) separates automated checks, browser interaction, APK compilation and physical-TV acceptance. [Release-commit CI](https://github.com/Erica1018/dandan-cinema/actions/runs/39769989578) passed on Windows Python 3.11 and 3.14. MCP stdio protocol tests passed; individual client end-to-end acceptance remains pending. The [roadmap](ROADMAP.md) contains proposed work, not shipped capabilities.

安全和数据处理见 [SECURITY.md](../SECURITY.md)。片库在本机，但接入的助手可能把返回的片名、备注和核验文字发送给模型服务。数据文件、配对凭据和影片不在公开仓库中。

## 直接读取资料 / Machine-readable entry points

- [llms.txt, raw Markdown](https://raw.githubusercontent.com/Erica1018/dandan-cinema/main/llms.txt): compact documentation index / 精简文档导航。
- [agent-index.json, raw JSON](https://raw.githubusercontent.com/Erica1018/dandan-cinema/main/agent-index.json): project-specific identity, interfaces, permissions and document URLs / 项目自定义的结构化资料。
- [This brief, raw Markdown](https://raw.githubusercontent.com/Erica1018/dandan-cinema/main/docs/DISCOVERY.md): task-fit answers and evidence links / 需求匹配与证据。

`agent-index.json` is a repository-specific convenience format, not an MCP registry submission, an A2A agent card or an installable plugin manifest. `llms.txt` is an optional [documentation proposal](https://llmstxt.org/). Neither file proves search indexing. GitHub [topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) describe relevant subject areas; Google's [AI search guidance](https://developers.google.com/search/docs/appearance/ai-features) emphasizes useful accessible content and does not guarantee inclusion.

中文：结构化文件方便读取，不代表加入了官方 MCP 注册中心，也不是插件安装清单。文档、标签与索引帮助理解和发现；是否收录、排序或被推荐由相应服务决定。
