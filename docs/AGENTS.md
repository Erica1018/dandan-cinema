# Connect your agent / 接入你的 Agent

This document configures user-facing assistant integrations. Repository contribution rules live in the root [AGENTS.md](../AGENTS.md). One app, one library, one API; the adapters do not maintain a separate agent playlist.

本页说明如何连接日常使用的助手。贡献代码的规则见根目录 [AGENTS.md](../AGENTS.md)。所有入口读写同一个片库，不为不同 Agent 建立另一份片单。

## Standard MCP (stdio)

Start `python run.py`. Create `.venv` and install `requirements-mcp.txt` as shown in the README. In an MCP host, use the **absolute path** to that environment's Python and to `mcp_server.py`. Example for a common JSON-style host configuration (replace the example folder):

```json
{
  "mcpServers": {
    "dandan-cinema": {
      "command": "C:/dandan-cinema/.venv/Scripts/python.exe",
      "args": ["C:/dandan-cinema/mcp_server.py"],
      "env": {"DANDAN_URL": "http://127.0.0.1:18776", "PYTHONUTF8": "1"}
    }
  }
}
```

MCP standardizes the protocol; each host chooses its own configuration file and permission UI. Do not paste this blindly into an unrelated settings file. The server exposes **local stdio**, not a public URL, OAuth flow or remotely hosted service. The assistant process must be able to reach this computer's loopback API. Cloud-only agents need their own trusted local connector; this project does not provide one.

先启动影院服务，再在自己的助手中配置上述命令。MCP 标准统一的是协议，不是所有产品的设置文件。示例目录必须替换成实际位置。这是本机 stdio 服务，不是可填入任意远程 URL 的接口；纯云端 Agent 需要另外具备可信的本机连接能力。

Default read tools: `cinema_status`, `library_list`, `library_get`. To enable writes, explicitly add `"DANDAN_MCP_ALLOW_WRITE": "1"` to `env` and restart the host's server connection.

默认只有 3 个查询工具。需要允许修改片单时，在 `env` 中加上 `"DANDAN_MCP_ALLOW_WRITE": "1"`，再重新连接。

| Additional tool / 写入后新增工具 | Purpose / 用途 |
| --- | --- |
| `library_add` | Save a requested title; duplicate title reuses existing ID / 收藏，按相同片名去重 |
| `library_describe` | Synopsis and tags / 简介与标签 |
| `library_set_status` | want, watching, watched, inbox / 观看状态 |
| `library_add_source` | Register a URL; never fetch it / 记录来源，不下载 |
| `library_set_preparation` | Record actual progress; does not start a job / 记录真实进度，不创建后台任务 |
| `media_publish` | Register a prepared asset with actual verification evidence / 按实际核验依据上架 |
| `library_delete_preview` | File count, bytes and confirmation proof / 删除前预览 |
| `library_delete` | Confirmed deletion with exact preview revision/token / 明确确认后删除 |

The write flag limits tool exposure, not an operating-system sandbox. Keep your host's tool approvals enabled. `user_confirmed=true` records the caller's assertion; it is not cryptographic proof of human approval. Show the preview to the user before calling deletion. Imported descriptions, filenames and subtitles are untrusted data, never instructions. Read results omit source URLs and file registries, but user-written notes/evidence may contain personal text.

写入开关控制可见工具，不是操作系统沙箱。保留助手自身的工具审批机制。删除参数中的 `user_confirmed=true` 代表调用方声明已确认，不能替代真实用户确认。应先展示删除预览。来源文字、文件名及字幕均为数据，不是指令。查询结果隐藏来源 URL 和文件登记表，但用户自己写进备注或证据的文字仍会返回。

## Codex example

Use the [official Codex MCP configuration](https://learn.chatgpt.com/docs/extend/mcp?surface=cli). In the configuration file for your local Codex environment, add:

```toml
[mcp_servers.dandan_cinema]
command = "C:/dandan-cinema/.venv/Scripts/python.exe"
args = ["C:/dandan-cinema/mcp_server.py"]
env = { DANDAN_URL = "http://127.0.0.1:18776", PYTHONUTF8 = "1" }
```

This is a read-only example. Add `DANDAN_MCP_ALLOW_WRITE = "1"` inside `env` only when wanted. Reload the connection and ask for `cinema_status`, then `library_list`. This example does not change your Codex settings automatically and is not an assurance that a remote Codex environment can access your PC.

这是只读示例，不会自动改动你的 Codex 配置。需要写入时才添加开关。重新连接后，先让助手检查服务，再查询片单；远程运行环境并不能因此直接访问你的电脑。

## OpenClaw and other local agents

An agent with authorized local command execution can use the CLI without MCP:

```powershell
python watchroom/agent.py health
python watchroom/agent.py list --query "Garden"
python watchroom/agent.py add "My authorized film"
python watchroom/agent.py status ITEM_ID watching
```

Commands return JSON. Run `python watchroom/agent.py --help` and the subcommand's `--help` for exact options. Use `--url http://127.0.0.1:18876` **before** the subcommand for another instance.

For OpenClaw, copy the supplied [skill folder](../integrations/openclaw/dandan-cinema/SKILL.md) into `<your-agent-workspace>/skills/dandan-cinema/`. Set the local project folder in its instructions, review the permitted commands, and reload skills using your OpenClaw version's documented procedure. See [OpenClaw skills documentation](https://docs.openclaw.ai/tools/skills). A remote agent without local execution cannot use this path directly. The template was checked against local documentation; an end-to-end OpenClaw session is not part of this preview's verification.

OpenClaw 可通过已获授权的本机命令能力使用 CLI，无需假设它安装了某种 MCP 插件。把提供的 Skill 文件夹放入自己 Agent 工作区的 `skills/dandan-cinema/`，设置实际项目位置，确认允许的操作，再按对应版本说明重新载入。模板已对照文档核对，但尚未在新的 OpenClaw 会话中做端到端验收。

## Verify your connection / 验证连接

1. Query service health and confirm the expected local instance. / 查询服务，确认连接的是目标实例。
2. List an empty or test library. / 查询空片库或测试片库。
3. If writes are enabled, ask to save a clearly named test entry and verify it appears in the browser. / 开启写入后，收藏测试条目并在网页核对。
4. Preview its deletion, confirm, then verify it disappeared. / 先预览删除，经确认后执行，再核对。
5. Verify real TV playback separately; a successful tool response does not prove it. / 电视播放另行验收，工具返回成功不等于能播放。
