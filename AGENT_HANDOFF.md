# Shared handoff

## 2026-10-22 19:27 (UTC+8) — Public preview published [agent:codex]

- Status: completed and publicly delivered. The owner authorized public GitHub distribution, Chinese/English support and vendor-neutral agent access.
- Source: an explicitly selected application-only extraction from the owner's working home cinema. Private deployment configuration, history, media and credentials are excluded.
- Completed: separate public application/data identity; bilingual desktop/TV UI; empty first-run library; setup/install scripts; local MCP/CLI adapters; MIT license; installation/API/contribution/security/discovery documentation; promotion drafts.
- Key files: `run.py`, `setup_tv.py`, `mcp_server.py`, `watchroom/client.py`, `watchroom-tv/`, both READMEs, `docs/INSTALL*`, `docs/AGENTS.md`, `docs/VALIDATION.md`, `docs/PROMOTION.md`.
- Validation: 25 isolated automated tests passed on Windows Python 3.14, including real MCP stdio. Edge add/search/language/deletion/mobile checks passed. Final Android APK build/signature verification passed. Release-tree review excluded private runtime/media/credentials. Hosted CI and published release evidence are recorded on GitHub.
- Delivery: [public repository](https://github.com/Erica1018/dandan-cinema), MIT recognized by GitHub, [published preview and APK](https://github.com/Erica1018/dandan-cinema/releases/tag/v0.1.0-preview.1). The release targets `cdd21782f576a0447ac187895e620cb67ef9e631`; subsequent main-branch documentation records delivery evidence.
- Hosted validation: [release commit CI](https://github.com/Erica1018/dandan-cinema/actions/runs/39769989578) passed on Windows Python 3.11 and 3.14. GitHub's uploaded APK SHA-256 matches local `1b43e3574aeb837e091e6c2797fa26b302c7c493adbba7ff643139b5fdaafbfb`; size 2,552,497 bytes. The release is public, not a draft, and marked prerelease.
- Next: fresh-user installation and physical-TV acceptance before broader promotion. Maintain one public source repository; installed private instances are independent data/configuration, not an alternate source of release claims.
- Blockers: none.
- Data gaps: public-package real TV playback; client-specific end-to-end MCP/CLI acceptance; fresh-user setup; other OS/device compatibility. See `docs/VALIDATION.md`.
- Social publication: not performed. Drafts only; posting requires the owner's chosen destination and authorization.
