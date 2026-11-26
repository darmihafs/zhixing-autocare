#!/usr/bin/env bash
set -euo pipefail

# =====================================================================
# 洪兴洗车电商服务平台 - Linux 一键停止脚本
# 仅精确停止本项目 8 个 Java 进程，不误杀宿主机其他 Java 服务或 Docker 中间件
# =====================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/logs/pids"

SERVICES=(
  "gateway"
  "ai-service"
  "review-service"
  "pay-service"
  "order-service"
  "shop-service"
  "product-service"
  "user-service"
)

stop_pid() {
  local svc="$1"
  local pid="$2"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  echo "[STOP] Sending SIGTERM to $svc (PID=$pid)..."
  kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 15); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "[OK]   $svc stopped."
      return 0
    fi
    sleep 1
  done
  echo "[WARN] $svc (PID=$pid) did not exit gracefully; sending SIGKILL..."
  kill -KILL "$pid" 2>/dev/null || true
}

for svc in "${SERVICES[@]}"; do
  jar_pattern="${svc}/target/${svc}-1.0.0.jar"
  pid_file="$PID_DIR/${svc}.pid"
  stopped=0

  if [[ -f "$pid_file" ]]; then
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      cmdline="$(tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null || true)"
      if [[ "$cmdline" == *"$jar_pattern"* ]]; then
        stop_pid "$svc" "$pid"
        stopped=1
      fi
    fi
    rm -f "$pid_file"
  fi

  if [[ "$stopped" -eq 0 ]] && command -v pgrep >/dev/null 2>&1; then
    while IFS= read -r pid; do
      [[ -n "$pid" ]] && stop_pid "$svc" "$pid"
    done < <(pgrep -f "$jar_pattern" || true)
  fi
done

echo "Done stopping HongXing services."
