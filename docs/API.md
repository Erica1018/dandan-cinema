# Local API contract / 本机接口约定

API version **1**, served by `run.py` at `http://127.0.0.1:18776`. UTF-8 JSON. Do not expose the editor on a LAN or public interface. The Python [client](../watchroom/client.py) is the reference adapter.

| Route | Method | Contract |
| --- | --- | --- |
| `/api/health` | GET | Application name, version, API version |
| `/api/state` | GET | Private raw state: `revision`, `items`, `csrf`; local adapters only |
| `/api/item` | POST | `{revision, item}`; upsert by stable item ID; omit ID to add |
| `/api/delete-preview` | POST | `{revision, id}`; returns count, bytes, shared files and confirmation proof |
| `/api/delete` | POST | `{revision, id, confirmation}`; confirmed coupled deletion |

All writes require `X-Watchroom-Token` from the current state. Host and, when supplied, Origin must match the local editor. Reload state after HTTP 409; do not force an old revision. A deleted ID cannot be resurrected through updates. HTTP 400 means invalid input, 403 access denied, 404 missing operation/item, 409 conflict or protected files. Deletion retries can return a prior receipt.

`item` fields include `title`, `category`, `status`, `synopsis`, `note`, `tags`, `sources`, `preparation`, `playback`, `artwork` and managed `files`. Schema validation is implemented by `server.clean_item`. `status`: `want`, `watching`, `watched`, `inbox`. Source kinds: `platform`, `cloud`, `direct`, `local`. Registering a source never fetches it. Use canonical categories `电影`, `剧集`, `纪录片`, `视频`, `其他` for the built-in filters; the UI translates these labels.

`preparation.stage`: `saved`, `searching`, `needs_access`, `preparing`, `ready`, `unavailable`. `ready` needs an existing hashed asset, affirmative identity/completeness checks and evidence. Optional original-audio and bilingual-subtitle checks must remain false when unverified. A rename invalidates identity-dependent metadata. External originals are not implicitly managed files.

The optional paired-TV service uses a separate private-network port, `X-Watchroom-Pair`, a configured TV address, and sanitized `/library`, `/media/<asset>.mp4`, `/media/<asset>.json`, `/art/<asset>.jpg` responses. It accepts only the two `/manage/delete-preview` and `/manage/delete` write routes. It is not the MCP endpoint.

Use stdio MCP or the CLI for agent integrations; avoid returning raw `/api/state` to a model. Add source adapters outside the core API, obtain authorization for source access, and send validated metadata through this same API. A pending stage does not imply a running scheduler.

中文摘要：这是本机 JSON 接口，所有写入带当前令牌与版本号。发生冲突后重新读取，不能强行覆盖。收藏、来源、准备、可播放是不同状态；已核验必须有真实证据。电视接口单独配对，不作为 MCP 入口；新 Agent 和来源适配器都应沿用同一个片库接口。
