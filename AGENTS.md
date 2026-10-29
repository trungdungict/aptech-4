# Agent instructions (Aptech-4)

This repository uses a **persistent Cursor playbook**. Do not rely on chat history.

## Before any implementation

1. Read [`.cursor/PROJECT_PLAYBOOK.md`](.cursor/PROJECT_PLAYBOOK.md)
2. Read [`.cursor/state/PROGRESS.md`](.cursor/state/PROGRESS.md), [`TASKS.md`](.cursor/state/TASKS.md), [`CURRENT_SESSION.md`](.cursor/state/CURRENT_SESSION.md), [`BLOCKERS.md`](.cursor/state/BLOCKERS.md)
3. Continue the **next task ID** recorded in progress/session files
4. Follow [`.cursor/rules/`](.cursor/rules/) (governance + resume always apply)

## Completion rule

Mark a task `DONE` only after acceptance criteria, real verification commands, and recorded evidence in `TASKS.md`.

## Resume prompt

See the copy-paste block at the end of `.cursor/PROJECT_PLAYBOOK.md`.
