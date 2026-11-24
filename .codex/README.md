# Open Karnan Kudumbam in Codex

This repository is prepared for Codex with project instructions, a project lead, focused subagents and project-scoped MCP connections.

## Codex web
1. Open https://chatgpt.com/codex
2. Connect GitHub if not already connected.
3. Choose repository Aksheytechytweek/morningo_stuff.
4. Select branch karnan-kudumbam-complete-integration (do not work on main).
5. Trust the repository so Codex loads .codex/config.toml.
6. Start with: "Read AGENTS.md and .codex/PROJECT_CONTEXT.md. Inspect the project and verify current build and live-price deployment status before changing anything."

## Local Windows setup
After cloning the repository and checking out the integration branch, open PowerShell in the repo root and run:
- git clone https://github.com/Aksheytechytweek/morningo_stuff.git
- cd morningo_stuff
- git checkout karnan-kudumbam-complete-integration
- codex .

For the desktop app, run codex app . if supported by the installed Codex build. Otherwise open Codex and select this folder.

## Configured MCP servers
Project-scoped .codex/config.toml declares:
- GitHub remote MCP (OAuth); write operations require approval.
- Cloudflare API MCP (OAuth); all actions require approval.
- Cloudflare docs MCP (read-only documentation).
- OpenAI developer docs MCP (read-only documentation).

On first use, authenticate GitHub and Cloudflare when prompted. Inspect active servers using codex mcp list or /mcp. Project config is loaded only after you trust the repository.
Firebase account/plugin access is separate and must be connected by the user if Firebase console actions are needed.

## Security
No API keys, Firebase service account JSON, Cloudflare token, Resend key, GitHub PAT or signing material is stored in this repository. Keep credentials in Codex OAuth storage, local environment, Worker secrets or GitHub Actions secrets as appropriate.
