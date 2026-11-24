# Karnan Kudumbam

Android household grocery, expense and smart-basket manager for a 3-adult home in Madipakkam, Chennai (PIN 600091), with a ₹15,000 monthly food/grocery budget.

## Codex workspace
This repository is prepared for OpenAI Codex. Start here:
- Agent rules: [AGENTS.md](AGENTS.md)
- Project Codex setup: [.codex/README.md](.codex/README.md)
- Full project handoff: [.codex/PROJECT_CONTEXT.md](.codex/PROJECT_CONTEXT.md)
- Project MCP configuration: [.codex/config.toml](.codex/config.toml)
- Custom Codex agents: [.codex/agents/](.codex/agents/)

Open [Codex](https://chatgpt.com/codex), connect this GitHub repository and select branch `karnan-kudumbam-complete-integration`. Do not make project changes on `main`.

## Project
- Android application: `karnan-kudumbam/MadipakkamGroceryManager`
- Backend Cloudflare Worker: `karnan-kudumbam/MadipakkamGroceryManager/backend`
- Backend API and deployment notes: [backend README](karnan-kudumbam/MadipakkamGroceryManager/backend/README.md)
- Core policies: offline-first, single-store basket, manual price checks, Rewalto free-credits-only, exact PIN and freshness validation.

## Build and test
Backend: `cd karnan-kudumbam/MadipakkamGroceryManager/backend; npm install; npm test`

Android: `cd karnan-kudumbam/MadipakkamGroceryManager; gradle --no-daemon assembleDebug`

See `AGENTS.md` and `.codex/PROJECT_CONTEXT.md` for full setup, build and security instructions. Never commit service credentials or provider API keys.
