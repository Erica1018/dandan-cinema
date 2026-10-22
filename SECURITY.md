# Security / 安全

This preview is designed for one trusted local user and a trusted private network. The editor binds to loopback; its browser writes use a request token, Host and Origin checks. The optional TV service binds to the configured private address and checks its paired token and source IP. TV transport is HTTP, not encrypted; it is unsuitable for public/shared hostile networks. Do not expose either port to the internet.

MCP is local stdio. Read-only defaults limit tools, not the host process's OS permissions. A compromised local account or a broadly authorized assistant can still read local files. Library notes/evidence sent to an AI host may leave the computer under that host's data policy. Deletion confirmation relies on the host honoring the actual user's decision.

Never publish `runtime/`, `.env`, credentials, pairing files, signing keys, account cookies, media links containing access tokens, real library exports or private device addresses. The release excludes these. Review all issue attachments before submitting them.

Report suspected vulnerabilities privately using GitHub's **Security → Report a vulnerability** when available. If the private option is unavailable, open an issue asking for a private reporting channel without exploit details or secrets. Only the latest preview receives fixes; there is no guaranteed response SLA.

中文：本项目适用于可信本机用户和家庭局域网。电视传输未加密，不用于公网或不可信共享网络。MCP 权限开关不是操作系统隔离；请保留助手审批，注意外接模型的数据策略。勿上传运行目录、令牌、签名、真实片库或带访问凭据的链接。安全问题优先通过 GitHub 私密漏洞报告反馈；入口不可用时，只公开请求私人报告渠道，不公开漏洞细节或秘密。
