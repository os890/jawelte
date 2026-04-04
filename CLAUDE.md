# Project Guidelines

## Documentation Files

### README.md
- Primary project documentation file
- **DO NOT read automatically** - only read when user explicitly asks
- Update when project structure or setup changes significantly
- Should reflect current project state and setup instructions

### LICENSE
- Contains project licensing information
- **DO NOT read automatically** - only read when user explicitly asks
- Do not modify without explicit user permission
- Consult when questions about licensing or usage rights arise

### mission.md
- Consult ONLY when:
  - Starting a new feature
  - After context compaction (to ensure no mission details are lost)

### architecture.md
- Consult when implementing, changing, or reviewing source code
- Contains technical architecture and design decisions

### diary.md
- **DO NOT read diary.md without user permission** (may be too large)
- **When adding entries**: Append to the file WITHOUT reading it first
- **Before every git commit**: Summarize the completed task and add to diary.md

### team.md and User Identity
- team.md contains team identity information:
  - **User**: os890 <open.source.890@gmail.com>
  - **AI Assistant**: Claude
- **When user name or email is needed**: Ask if you should use the stored identity or something else
- **DO NOT use user identity automatically** - always confirm first
- If an AI assistant name is configured, use it in `Co-Authored-By` lines in commit messages
- If the user addresses you by the configured AI name, respond naturally to it
- This file is excluded from git commits

### idea.md
- **DO NOT read automatically** - only read when user explicitly asks
- **DO NOT add content automatically** - only add when user explicitly requests
- Used to store ideas from brainstorming phases
- Organize ideas as they are collected throughout the project

### todo.md
- **DO NOT read automatically** - only read when user explicitly asks
- **DO NOT add content automatically** - only add when user explicitly requests
- Used to store todo-tasks for later
- Track tasks that come up during work but should be addressed later

### errors/ folder
- Used by the user to provide error files (screenshots, logs, stack traces, terminal output, etc.)
- **DO NOT scan this folder automatically or on a schedule**
- Read and analyze files here when the user asks you to look at them
- If you notice new files during other work, you may mention them — but do not repeatedly check on your own
- After the error is resolved, ask the user if the error files should be cleaned up
- This folder is excluded from git (added to .gitignore)
- If a filesystem monitor was set up during init, it will notify you when files are created or updated here

### blog/ and presentation/ folders
- **DO NOT consult automatically**
- Only access when user specifically requests or for special tasks

## Build and Run Environment

**Default execution approach:** no container

- When building or running code/applications, use this approach as the default
- Options:
  - **podman container**: Use Podman containers for isolated, reproducible builds
  - **no container**: Execute locally without containerization
  - **custom rule**: 

## Working with CLAUDE.md
- **Never save anything to CLAUDE.md without asking first**
- User must approve all additions/changes to this file

## Questions and Clarifications
- Questions are OK at any time if you're unsure about options or have a better idea
- Only skip questions if explicitly told to do so for a task
- Always ask rather than assume

## Commit Strategy
- **Strategy: Commit ASAP**
- **THIS IS A STANDING INSTRUCTION FROM THE USER**: You are authorized and expected to commit source code automatically after every file creation or modification. Do NOT ask "should I commit this?" for source code — just commit it. This authorization was explicitly granted during project initialization.
- Every saved source code change must be committed to git promptly — nothing stays uncommitted
- The commit must happen as the very next action after writing/editing a source code file — do not continue with other work before committing
- For non-code files created on user request: confirm before committing
- Use commit message prefixes to indicate state:
  - `UNTESTED:` — Code has not been tested yet (e.g. `UNTESTED: add user login endpoint`)
  - `UNTESTED (known issues):` — Untested with known problems (e.g. `UNTESTED (known issues): add login - error handling incomplete`)
  - `FIXED:` — A bug or issue was resolved (e.g. `FIXED: login endpoint error handling`)
  - `WORKING:` — All tests run (user-requested or self-initiated) have passed (e.g. `WORKING: user login endpoint with full test coverage`)
- When an `UNTESTED` commit is later verified or fixed, create a follow-up commit with the appropriate prefix

## Git Workflow
- Summarize completed task in diary.md before each commit
- Follow user's preferences for commit messages and workflow
- **Commit documentation changes promptly**: Every change to markdown files tracked by this CLAUDE.md should be committed to git as soon as possible to avoid losing data or steps of edits
- This includes changes to: mission.md, architecture.md, diary.md, idea.md, todo.md, and other project documentation files