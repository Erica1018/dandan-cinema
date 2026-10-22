# Dandan Cinema · 蛋蛋影院

[简体中文](README.zh-CN.md) · [Install](docs/INSTALL.md) · [Connect your agent](docs/AGENTS.md) · [Download TV preview](https://github.com/Erica1018/dandan-cinema/releases) · [FAQ](docs/FAQ.md)

**A personal watchlist and Android TV player that your own AI assistant can help manage.** Save a title on your computer, prepare your own media, and open the same library on your TV. Chinese and English interfaces are included. No AI account is required for manual use.

![Dandan Cinema English desktop interface with a fictional demo entry](docs/images/desktop-en.png)

This is **0.1.0-preview.1**, the first public extraction of a personal home cinema project. It is intended for early adopters comfortable with a few setup commands. The screenshot uses fictional demo content; new installations start empty.

## What it does

- Local watchlist: titles, notes, tags, short descriptions, viewing and preparation states.
- Native Android TV interface: remote navigation, local media playback, resume, seek controls, and optional Chinese/English SRT subtitle tracks.
- One library for people and agents: a loopback HTTP API, a JSON CLI, and a standard **stdio MCP server**.
- MCP starts with 3 read tools. Explicit opt-in enables 8 additional write/management tools. Deletion uses a preview and confirmation.
- Local storage: no application telemetry or cloud account. Connecting an AI host may send returned library text to that host/provider; choose its permissions accordingly.

The project does not supply films, discover or download sources automatically, bypass subscriptions/DRM, or promise that arbitrary platform URLs play on a TV. You provide media you are entitled to use. A saved webpage is a source reference, not proof of playable media.

## Start on Windows

Install Python 3.11 or newer, download and extract this repository, then open a terminal in the extracted folder:

```powershell
python run.py
```

Open **http://127.0.0.1:18776**. Or double-click `Start.cmd` after Python is installed. The basic library uses only Python's standard library. Keep the terminal running; Ctrl+C stops it.

For TV pairing, media preparation and troubleshooting, follow the [step-by-step installation guide](docs/INSTALL.md). Optional MCP dependencies are installed separately:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-mcp.txt
```

Then configure your assistant using [MCP, Codex and OpenClaw examples](docs/AGENTS.md). Configuring an example is separate from verifying that client in your environment.

## Compatibility and evidence

| Component | First preview scope |
| --- | --- |
| Computer | Windows installation path tested; Python 3.11+ required |
| Browser | Desktop Chromium/Edge flow tested; responsive layout checked |
| Android TV | APK compiled and signature verified; Android 5+ minimum, legacy target SDK 28; sideloading only |
| Physical TV | Earlier private version used on an Android 9 TV; this public package still needs fresh device acceptance |
| MCP | Real stdio initialization, tool discovery, read/write and deletion flow tested with the official Python SDK |
| Agent clients | Codex configuration and OpenClaw skill/CLI examples provided; no claim of an end-to-end test in every client |
| Media | Prepared H.264/AAC MP4; optional SRT; direct URL compatibility depends on codec, network and device |

This is a trusted-home-network preview, not an internet-facing media service. TV traffic is HTTP with a pairing token and IP restriction. See [security boundaries](SECURITY.md) and [release validation](docs/VALIDATION.md).

## Contribute or build on it

See [contributing](CONTRIBUTING.md), [API contract](docs/API.md), [Android build instructions](docs/BUILD.md), and the [roadmap](docs/ROADMAP.md). Useful first contributions include installation reports, device compatibility, additional subtitle languages, and source adapters with clear authorization boundaries.

Code, documentation and original repository artwork are under the [MIT license](LICENSE); see [brand usage](BRAND.md) and [third-party notices](THIRD_PARTY_NOTICES.md). This allows commercial use while retaining the license notice. It does not grant rights to users' media or imply official endorsement of forks.

Created by [Erica1018](https://github.com/Erica1018). Built with AI assistance; releases remain subject to recorded tests and human feedback.
