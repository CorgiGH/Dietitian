#!/usr/bin/env bash
# Install pre-commit hook into .git/hooks/ (not git-tracked).
# Run once per clone. On Windows, use Git Bash / WSL.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
install -m 0755 "$SCRIPT_DIR/pre-commit" "$REPO_ROOT/.git/hooks/pre-commit"
echo "Installed pre-commit hook -> $REPO_ROOT/.git/hooks/pre-commit"
