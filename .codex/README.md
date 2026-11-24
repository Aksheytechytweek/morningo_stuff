# Open Karnan Kudumbam in Codex

This repository is prepared for Codex with project instructions, a project lead, focused subagents and project-scoped MCP connections.

## Preferred: Codex Cloud with GitHub (no ZIP)

A repository ZIP is not needed for the normal Codex Cloud workflow. Connect the GitHub repository so Codex can check it out as a Git repository.

1. Open https://chatgpt.com/codex on the web or desktop.
2. Start a new task and choose **Work in → Cloud**.
3. Open **Select environment → Create environment**.
4. Connect GitHub if prompted, then select `Aksheytechytweek/morningo_stuff`.
5. Choose **Get started** and let Codex inspect and prepare the repository.
6. In the first task, tell Codex to fetch and switch to `karnan-kudumbam-complete-integration` before reading or modifying files. Do not work on `main`.
7. Review the setup report, finish any requested setup, and publish the environment.

Use this first-task prompt:

```text
In Aksheytechytweek/morningo_stuff, fetch and switch to the
karnan-kudumbam-complete-integration branch before inspecting or
editing project files. Do not modify main. Read AGENTS.md and
.codex/PROJECT_CONTEXT.md, inspect the project, and report the
build/test status. Do not deploy, access production data, or use
paid services.
```

If the repository is missing from the picker, verify that Codex is connected to the same GitHub account that can access it. Being able to use a Codex environment does not automatically grant access to a GitHub repository.

## Local Windows setup

Use Git rather than downloading a ZIP. This directly checks out the intended branch:

```powershell
git clone --branch karnan-kudumbam-complete-integration --single-branch https://github.com/Aksheytechytweek/morningo_stuff.git
cd morningo_stuff
codex .
```

This requires Git and the Codex CLI to be installed. If you use the Codex desktop app, open the cloned `morningo_stuff` folder and select the `karnan-kudumbam-complete-integration` branch.

## Configured MCP servers

Project-scoped `.codex/config.toml` declares:
- GitHub remote MCP (OAuth); write operations require approval.
- Cloudflare API MCP (OAuth); all actions require approval.
- Cloudflare docs MCP (read-only documentation).
- OpenAI developer docs MCP (read-only documentation).

On first use, authenticate GitHub and Cloudflare when prompted. Inspect active servers using `codex mcp list` or `/mcp`. Project config is loaded only after this repository is trusted.
Firebase account/plugin access is separate and must be connected by the user if Firebase console actions are needed.

## Security

No API keys, Firebase service account JSON, Cloudflare token, Resend key, GitHub PAT or signing material is stored in this repository. Keep credentials in Codex OAuth storage, local environment, Worker secrets or GitHub Actions secrets as appropriate.
