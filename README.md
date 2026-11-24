# Karnan Kudumbam

Android household grocery, expense and smart-basket manager for a 3-adult home in Madipakkam, Chennai (PIN 600091), with a ₹15,000 monthly food/grocery budget.

## Open in Codex — ZIP download is not required

**Recommended: connect GitHub directly.** Do not download a repository ZIP and re-upload it to Codex for the normal Cloud workflow.

1. Open [Codex](https://chatgpt.com/codex) on the web or desktop.
2. Choose **Work in → Cloud → Create environment**.
3. Connect the GitHub account that can access `Aksheytechytweek/morningo_stuff`.
4. Select the repository and choose **Get started**.
5. When setup is ready, start a task with this instruction:

   > In `Aksheytechytweek/morningo_stuff`, fetch and switch to `karnan-kudumbam-complete-integration` before inspecting or editing project files. Do not make changes on `main`. Then read `AGENTS.md` and `.codex/PROJECT_CONTEXT.md`, inspect the Android and backend setup, and report the build/test status without deploying or using paid services.

6. Review the setup report and publish the environment. Start a new task from that published environment.

If the repository does not appear, check that Codex is connected to the same GitHub account that has access to the repository. Cloud environment access and GitHub repository access are separate permissions.

## Project handoff files

- Agent rules: [AGENTS.md](AGENTS.md)
- Project Codex setup: [.codex/README.md](.codex/README.md)
- Full project handoff: [.codex/PROJECT_CONTEXT.md](.codex/PROJECT_CONTEXT.md)
- Project MCP configuration: [.codex/config.toml](.codex/config.toml)
- Custom Codex agents: [.codex/agents/](.codex/agents/)

**Active project branch:** `karnan-kudumbam-complete-integration`. Keep `main` untouched unless explicitly requested.

## Local Windows fallback

If you prefer working locally or Cloud repository setup is unavailable, use Git instead of a ZIP:

```powershell
git clone --branch karnan-kudumbam-complete-integration --single-branch https://github.com/Aksheytechytweek/morningo_stuff.git
cd morningo_stuff
codex .
```

This checks out the integration branch directly and preserves Git history and branch context.

## Project

- Android application: `karnan-kudumbam/MadipakkamGroceryManager`
- Backend Cloudflare Worker: `karnan-kudumbam/MadipakkamGroceryManager/backend`
- Backend API and deployment notes: [backend README](karnan-kudumbam/MadipakkamGroceryManager/backend/README.md)
- Core policies: offline-first, single-store basket, manual price checks, Rewalto free-credits-only, exact PIN and freshness validation.

## Build and test

Backend: `cd karnan-kudumbam/MadipakkamGroceryManager/backend; npm install; npm test`

Android: `cd karnan-kudumbam/MadipakkamGroceryManager; gradle --no-daemon assembleDebug`

See `AGENTS.md` and `.codex/PROJECT_CONTEXT.md` for full setup, build and security instructions. Never commit service credentials or provider API keys.
