#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_NAME="__EXTRACT_COMMAND__"
SKILL_NAME="$(find "$PACKAGE_DIR/skills" -mindepth 1 -maxdepth 1 -type d -name 'endpoint-rule-author-*' -print -quit 2>/dev/null | xargs -r basename)"
OUTPUT_NAME="endpoint-rules-__LANGUAGE__.zip"

usage() { printf 'Usage: ./start.sh [--project <directory>] [--agent codex|claude|other]\n'; }

project_dir=""
agent=""
while (($#)); do
  case "$1" in
    --project) project_dir="${2:-}"; shift 2 ;;
    --agent) agent="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$project_dir" ]]; then
  read -r -p '项目目录（默认当前目录）：' project_dir
  project_dir="${project_dir:-$PWD}"
fi
case "$project_dir" in
  '~') project_dir="$HOME" ;;
  '~/'*) project_dir="$HOME/${project_dir#\~/}" ;;
esac
[[ -d "$project_dir" ]] || { printf '项目目录不存在：%s\n' "$project_dir" >&2; exit 1; }
project_dir="$(cd "$project_dir" && pwd)"

if [[ -z "$agent" ]]; then
  printf '选择 Agent：\n  1) Codex\n  2) Claude Code\n  3) 其他 Agent\n'
  read -r -p '请输入序号：' choice
  case "$choice" in
    1) agent="codex" ;;
    2) agent="claude" ;;
    3) agent="other" ;;
    *) printf '无效选择。\n' >&2; exit 1 ;;
  esac
fi

if [[ -n "${XDG_DESKTOP_DIR:-}" ]]; then
  desktop_dir="$XDG_DESKTOP_DIR"
elif command -v xdg-user-dir >/dev/null 2>&1; then
  desktop_dir="$(xdg-user-dir DESKTOP)"
else
  desktop_dir="$HOME/Desktop"
fi
mkdir -p "$desktop_dir"

skill_file="$PACKAGE_DIR/skills/$SKILL_NAME/SKILL.md"
output_file="$desktop_dir/$OUTPUT_NAME"
export PATH="$PACKAGE_DIR/bin:$PATH"
export CODEGRAPH_ENDPOINT_OUTPUT="$output_file"

prompt="Read and follow the complete Skill at: $skill_file
Analyze the project at: $project_dir
Use the bundled $CLI_NAME command available on PATH. Discover, create, and validate every SER endpoint rule required by this project. Before finishing, collect every generated .ser file, verify none are omitted, and create exactly this ZIP file: $output_file. The ZIP must contain only .ser rule files. Report the absolute ZIP path."

case "$agent" in
  codex)
    command -v codex >/dev/null 2>&1 || { printf '未找到 codex，请先安装并登录 Codex CLI。\n' >&2; exit 1; }
    codex exec --approve-for-me -C "$project_dir" --add-dir "$desktop_dir" "$prompt"
    ;;
  claude)
    command -v claude >/dev/null 2>&1 || { printf '未找到 claude，请先安装并登录 Claude Code。\n' >&2; exit 1; }
    (cd "$project_dir" && claude --add-dir "$desktop_dir" --permission-mode acceptEdits "$prompt")
    ;;
  other)
    read -r -p 'Agent 可执行命令（不带参数）：' agent_command
    command -v "$agent_command" >/dev/null 2>&1 || { printf '未找到命令：%s\n' "$agent_command" >&2; exit 1; }
    (cd "$project_dir" && "$agent_command" "$prompt")
    ;;
  *) printf '不支持的 Agent：%s\n' "$agent" >&2; exit 1 ;;
esac

if [[ -f "$output_file" ]]; then
  printf '\n已生成：%s\n' "$output_file"
else
  printf '\nAgent 已结束，但未找到预期文件：%s\n' "$output_file" >&2
  exit 1
fi
