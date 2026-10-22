# FAQ / 常见问题

**What is Dandan Cinema? / 蛋蛋影院是什么？**

A local personal watchlist, an Android TV playback app, and optional tools for your own assistant to manage the same library. 它是本机私人片单、Android TV 播放应用，以及让自己的助手管理同一片库的可选接口。

**Do I need Codex or OpenClaw? / 必须用 Codex 或 OpenClaw 吗？**

No. Manual library use needs no AI account. Any compatible local stdio MCP host can try the server; local agents may use the CLI. Examples are not universal client certification. 不必。手动使用无需 AI；兼容本机 stdio MCP 的助手可尝试接入，有本机执行能力的 Agent 可使用 CLI。示例不代表所有客户端均已验证。

**Does it find or download movies? / 会自动找片、下载吗？**

Not in this public preview. You supply authorized sources; metadata/preparation tools record actual work. DRM, subscription bypass and all-platform URL parsing are not provided. 公开预览版不提供自动找片下载。用户提供授权来源，工具记录真实准备状态；不提供 DRM 绕过、会员绕过或全平台解析。

**What does bilingual mean? / 中英双语指什么？**

English and Chinese desktop/TV interface text and documentation. Media preparation accepts optional Chinese and English SRT tracks; it does not translate subtitles or guarantee that a given film has them. 指界面与文档支持中英文。可选加载中英文 SRT，不会自动翻译，也不保证每部影片都有双语字幕。

**Where are my data and AI messages stored? / 数据放在哪里？**

The app stores the library/media locally, with a TV cache on the paired device. There is no app telemetry. An assistant host may send queried titles, notes and evidence to its model provider. Refer to that host's settings. 片库和媒体在电脑，电视保留配对缓存。应用无遥测；外接助手可能将查询到的片名、备注和证据发送给模型服务，需看助手自身设置。

**Can it replace Jellyfin/Plex or support every TV? / 能替代完整媒体服务器，支持所有电视吗？**

This first preview focuses on a small personal library, Android TV remote use, and agent access. It does not claim their full feature set, multi-user hosting, all devices or codecs. 目前聚焦个人片单、电视遥控与 Agent 接入，不声称覆盖完整媒体服务器、多用户托管或所有设备格式。

**Can I use it commercially? / 可以商用吗？**

The MIT license permits commercial use of the repository software with its notice retained. It does not grant rights to films, platform services or trademarks. MIT 允许保留声明后商用软件，但不授予影片、平台服务或商标的权利。

**What is `llms.txt` for? / `llms.txt` 有什么用？**

It is a short documentation index for readers and tools. It is not an official universal discovery protocol or a promise of search inclusion. Google states that existing SEO fundamentals apply to AI search features, without additional technical requirements: [official guidance](https://developers.google.com/search/docs/appearance/ai-features). 它是方便人和工具读取的文档目录，不是通用收录协议或推荐保证。清楚、可访问、准确的说明更有用。
